package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.game.session.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameAlreadyOverException;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionActivistDeclarationRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.CarryOverState;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.MessagePayloads;
import io.github.temporalrift.game.shared.ProcessedEventRepository;
import io.github.temporalrift.game.shared.TimelineEventEnvelope;

@Component
class EraResolutionCompletedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(EraResolutionCompletedKafkaConsumer.class);
    private static final String EVENT_TYPE = GeneratedChannelContract.ERA_RESOLUTION_COMPLETED_EVENT_TYPE;
    private static final String CONSUMER = "session.era-resolution-completed";

    private final ProcessedEventRepository processedEventRepository;
    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;
    private final SessionActivistDeclarationRepository declarationRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SessionGameRulesPort gameRules;
    private final TimelineSessionWireMapper wireMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EraResolutionCompletedKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            GameRepository gameRepository,
            LobbyRepository lobbyRepository,
            SessionActivistDeclarationRepository declarationRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            SessionGameRulesPort gameRules,
            TimelineSessionWireMapper wireMapper,
            ObjectMapper objectMapper,
            Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
        this.declarationRepository = declarationRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.gameRules = gameRules;
        this.wireMapper = wireMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.session.era-resolution-completed")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(Message<Object> message) {
        var envelope = TimelineEventEnvelope.from(message);
        if (envelope.eventId() == null || message.getPayload() == null) {
            log.warn("Malformed record on timeline.events (missing eventId header or payload) — discarding");
            return;
        }
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
        if (!envelope.hasVersion(DomainEventEnvelope.SCHEMA_VERSION_V1)) {
            log.warn(
                    "Unsupported {} envelope version {} for event {} — skipping",
                    EVENT_TYPE,
                    envelope.version(),
                    envelope.eventId());
            return;
        }
        if (!processedEventRepository.tryMarkProcessed(envelope.eventId(), CONSUMER)) {
            log.debug("Duplicate {} event {} ignored", EVENT_TYPE, envelope.eventId());
            return;
        }

        var resolution =
                wireMapper.fromWire(MessagePayloads.read(objectMapper, message, EraResolutionCompletedPayload.class));
        if (!envelope.matchesGameId(resolution.gameId())) {
            return;
        }
        var carryOverEvents = resolution.terminalResolutions().stream()
                .filter(entry -> entry.terminalState() == EraResolutionCompleted.TerminalState.CASCADED
                        || entry.terminalState() == EraResolutionCompleted.TerminalState.STALLED)
                .sorted(Comparator.comparingInt(EraResolutionCompleted.TerminalResolution::revealIndex))
                .map(entry -> new PendingCarryOverEvent(
                        entry.eventId(),
                        CarryOverState.valueOf(entry.terminalState().name())))
                .toList();
        if (carryOverEvents.isEmpty()) {
            return;
        }
        var cascadedEventIds = resolution.terminalResolutions().stream()
                .filter(entry -> entry.terminalState() == EraResolutionCompleted.TerminalState.CASCADED)
                .sorted(Comparator.comparingInt(EraResolutionCompleted.TerminalResolution::revealIndex))
                .map(EraResolutionCompleted.TerminalResolution::eventId)
                .toList();

        var game = gameRepository
                .findByIdWithLock(resolution.gameId())
                .orElseThrow(() -> new GameNotFoundException(resolution.gameId()));
        final UUID collapsingEventId;
        if (cascadedEventIds.isEmpty()) {
            collapsingEventId = null;
        } else {
            try {
                collapsingEventId =
                        game.recordCascadedParadoxesInRevealOrder(cascadedEventIds, gameRules.maxCascadedParadoxes());
            } catch (GameAlreadyOverException _) {
                log.info("EraResolutionCompleted ignored for game {} — already over", resolution.gameId());
                return;
            }
        }
        if (game.status() == GameStatus.IN_PROGRESS) {
            game.recordPendingCarryOverEvents(carryOverEvents);
        }
        gameRepository.save(game);

        if (collapsingEventId != null) {
            publishTimelineCollapsed(game, resolution.eraNumber(), collapsingEventId);
        }
    }

    private void publishTimelineCollapsed(Game game, int eraNumber, UUID collapsingEventId) {
        var players = lobbyRepository
                .findById(game.lobbyId())
                .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()))
                .currentPlayers();
        var winnerIds = declarationRepository.findPlayerIdsTargeting(game.id(), eraNumber, collapsingEventId);
        var collapsed = buildTimelineCollapsed(game.id(), eraNumber, players, winnerIds);
        eventPublisher.publish(DomainEventEnvelope.create(
                game.id(), Game.AGGREGATE_TYPE, game.id(), DomainEventEnvelope.SCHEMA_VERSION_V1, collapsed, clock));
        applicationEventPublisher.publishEvent(collapsed);
    }

    private TimelineCollapsed buildTimelineCollapsed(
            UUID gameId, int eraNumber, List<LobbyPlayer> players, List<UUID> winnerIds) {
        var winners = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        var losers = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        for (var player : players) {
            var faction = player.faction();
            var result = new TimelineCollapsed.PlayerFactionResult(
                    player.playerId(), faction == null ? null : faction.name());
            if (winnerIds.contains(player.playerId())) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        return new TimelineCollapsed(gameId, eraNumber, winners, losers);
    }
}
