package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.domain.event.ParadoxCascaded;
import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.game.GameAlreadyOverException;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.lobby.LobbyNotFoundException;
import io.github.temporalrift.game.session.domain.lobby.LobbyPlayer;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@Component
class ParadoxCascadedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParadoxCascadedKafkaConsumer.class);
    private static final String EVENT_TYPE = "timeline.ParadoxCascaded";
    private static final String CONSUMER = "session.paradox-cascaded";
    private static final Set<Faction> COLLAPSE_WINNERS = Set.of(Faction.ERASERS, Faction.REVISIONISTS);

    private final ProcessedEventRepository processedEventRepository;
    private final GameRepository gameRepository;
    private final LobbyRepository lobbyRepository;
    private final SessionEventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SessionGameRulesPort gameRules;
    private final ObjectMapper objectMapper;

    ParadoxCascadedKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            GameRepository gameRepository,
            LobbyRepository lobbyRepository,
            SessionEventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            SessionGameRulesPort gameRules,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.gameRepository = gameRepository;
        this.lobbyRepository = lobbyRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.gameRules = gameRules;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.session.paradox-cascaded")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(InboundEnvelope envelope) {
        if (envelope.eventId() == null || envelope.payload() == null) {
            log.warn("Malformed envelope on timeline.events (missing eventId or payload) — discarding");
            return;
        }
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
        // Check version before claiming: claiming an unsupported version would permanently mark the
        // event processed, so it could never be reprocessed once this consumer learns to handle it.
        if (envelope.version() != 1) {
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

        var paradox = objectMapper.convertValue(envelope.payload(), ParadoxCascaded.class);
        var gameId = paradox.gameId();

        var game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        try {
            game.recordCascadedParadox(gameRules.maxCascadedParadoxes());
        } catch (GameAlreadyOverException _) {
            log.info("ParadoxCascaded ignored for game {} — already over", gameId);
            return;
        }
        gameRepository.save(game);

        if (game.status() == GameStatus.ENDED_BY_COLLAPSE) {
            // The lobby roster is the system of record for assigned factions; saga state is
            // workflow bookkeeping and must not be read as game data.
            var players = lobbyRepository
                    .findById(game.lobbyId())
                    .orElseThrow(() -> new LobbyNotFoundException(game.lobbyId()))
                    .currentPlayers();

            var collapsed = buildTimelineCollapsed(gameId, paradox.eraNumber(), players);
            eventPublisher.publish(DomainEventEnvelope.create(gameId, Game.AGGREGATE_TYPE, gameId, 1, collapsed));
            applicationEventPublisher.publishEvent(collapsed);
        }
    }

    private TimelineCollapsed buildTimelineCollapsed(UUID gameId, int eraNumber, List<LobbyPlayer> players) {
        var winners = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        var losers = new ArrayList<TimelineCollapsed.PlayerFactionResult>();
        for (var player : players) {
            var faction = player.faction();
            var result = new TimelineCollapsed.PlayerFactionResult(
                    player.playerId(), faction == null ? null : faction.name());
            if (faction != null && COLLAPSE_WINNERS.contains(faction)) {
                winners.add(result);
            } else {
                losers.add(result);
            }
        }
        return new TimelineCollapsed(gameId, eraNumber, winners, losers);
    }
}
