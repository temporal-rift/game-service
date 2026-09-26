package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.Comparator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.game.session.application.saga.TimelineCollapsePublisher;
import io.github.temporalrift.game.session.domain.event.EraResolutionCompleted;
import io.github.temporalrift.game.session.domain.game.GameAlreadyOverException;
import io.github.temporalrift.game.session.domain.game.GameNotFoundException;
import io.github.temporalrift.game.session.domain.game.GameStatus;
import io.github.temporalrift.game.session.domain.game.PendingCarryOverEvent;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.model.CarryOverState;
import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.MessagePayloads;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.TimelineEventEnvelope;

@Component
class EraResolutionCompletedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(EraResolutionCompletedKafkaConsumer.class);
    private static final String EVENT_TYPE = GeneratedChannelContract.ERA_RESOLUTION_COMPLETED_EVENT_TYPE;
    private static final String CONSUMER = "session.era-resolution-completed";

    private final ProcessedEventRepository processedEventRepository;
    private final EraSagaRepository eraSagaRepository;
    private final GameRepository gameRepository;
    private final TimelineCollapsePublisher collapsePublisher;
    private final SessionGameRulesPort gameRules;
    private final TimelineSessionWireMapper wireMapper;
    private final ObjectMapper objectMapper;

    EraResolutionCompletedKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            EraSagaRepository eraSagaRepository,
            GameRepository gameRepository,
            TimelineCollapsePublisher collapsePublisher,
            SessionGameRulesPort gameRules,
            TimelineSessionWireMapper wireMapper,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.eraSagaRepository = eraSagaRepository;
        this.gameRepository = gameRepository;
        this.collapsePublisher = collapsePublisher;
        this.gameRules = gameRules;
        this.wireMapper = wireMapper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.session.era-resolution-completed")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(Message<Object> message) {
        var envelope = TimelineEventEnvelope.from(message);
        if (envelope.eventId() == null || MessagePayloads.isEmpty(message)) {
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

        // Lock in saga-then-game order to match EraSagaAdvancer and avoid lock-order deadlocks.
        var saga = eraSagaRepository.findByGameIdWithLock(resolution.gameId());
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

        if (collapsingEventId == null) {
            return;
        }
        // Normal victory outranks same-era collapse: while the era saga still awaits this era's
        // scoring, the collapse fact stays recorded but undecided and EraSagaAdvancer makes the
        // single era-end decision once qualifiers are known. Only a late collapse — arriving after
        // the saga already left WAITING_SCORES for this era — ends the game immediately.
        var awaitingScoring = saga.filter(candidate -> candidate.status() == EraSagaStatus.WAITING_SCORES
                        && candidate.eraNumber() == resolution.eraNumber())
                .isPresent();
        if (awaitingScoring) {
            log.info(
                    "Collapse threshold reached for game {} era {} — deferring to scoring decision",
                    resolution.gameId(),
                    resolution.eraNumber());
            return;
        }
        if (game.status() != GameStatus.IN_PROGRESS) {
            log.info("EraResolutionCompleted ignored for game {} — already over", resolution.gameId());
            return;
        }
        game.endByCollapse();
        gameRepository.save(game);
        collapsePublisher.publishCollapse(game, resolution.eraNumber(), collapsingEventId);
    }
}
