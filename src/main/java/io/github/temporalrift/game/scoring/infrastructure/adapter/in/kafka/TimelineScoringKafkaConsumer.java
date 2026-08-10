package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.CHAIN_BROKEN_EVENT_TYPE;
import static io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.CHAIN_COMPLETED_EVENT_TYPE;
import static io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ERA_RESOLUTION_COMPLETED_EVENT_TYPE;
import static io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.OUTCOME_APPLIED_EVENT_TYPE;
import static io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.PARADOX_CASCADED_EVENT_TYPE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ChainBrokenPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ChainCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.OutcomeAppliedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxCascadedPayload;
import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.scoring.domain.port.out.TimelineOutcomeInboxRepository;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.MessagePayloads;
import io.github.temporalrift.game.shared.ProcessedEventRepository;
import io.github.temporalrift.game.shared.TimelineEventEnvelope;

@Component
class TimelineScoringKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TimelineScoringKafkaConsumer.class);

    private static final String CONSUMER = "scoring.timeline-events";
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            OUTCOME_APPLIED_EVENT_TYPE,
            CHAIN_COMPLETED_EVENT_TYPE,
            CHAIN_BROKEN_EVENT_TYPE,
            ERA_RESOLUTION_COMPLETED_EVENT_TYPE,
            PARADOX_CASCADED_EVENT_TYPE);

    private final ProcessedEventRepository processedEventRepository;
    private final TimelineOutcomeInboxRepository outcomeInboxRepository;
    private final EraScoringContextRepository contextRepository;
    private final EraScoringCompletionChecker completionChecker;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TimelineScoringWireMapper wireMapper;
    private final ObjectMapper objectMapper;

    TimelineScoringKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            TimelineOutcomeInboxRepository outcomeInboxRepository,
            EraScoringContextRepository contextRepository,
            EraScoringCompletionChecker completionChecker,
            ApplicationEventPublisher applicationEventPublisher,
            TimelineScoringWireMapper wireMapper,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.outcomeInboxRepository = outcomeInboxRepository;
        this.contextRepository = contextRepository;
        this.completionChecker = completionChecker;
        this.applicationEventPublisher = applicationEventPublisher;
        this.wireMapper = wireMapper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.scoring.timeline-events")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(Message<Object> message) {
        var envelope = TimelineEventEnvelope.from(message);
        if (envelope.eventId() == null || message.getPayload() == null) {
            log.warn("Malformed record on timeline.events (missing eventId header or payload) — discarding");
            return;
        }
        if (!SUPPORTED_EVENT_TYPES.contains(envelope.eventType())) {
            return;
        }
        // Check version before claiming: claiming an unsupported version would permanently mark the
        // event processed, so it could never be reprocessed once this consumer learns to handle it.
        if (!envelope.hasVersion(DomainEventEnvelope.SCHEMA_VERSION_V1)) {
            log.warn(
                    "Unsupported {} envelope version {} for event {} — skipping",
                    envelope.eventType(),
                    envelope.version(),
                    envelope.eventId());
            return;
        }
        if (!processedEventRepository.tryMarkProcessed(envelope.eventId(), CONSUMER)) {
            log.debug("Duplicate {} event {} ignored", envelope.eventType(), envelope.eventId());
            return;
        }

        switch (envelope.eventType()) {
            case OUTCOME_APPLIED_EVENT_TYPE -> handleOutcomeApplied(message);
            case CHAIN_COMPLETED_EVENT_TYPE -> handleChainCompleted(message);
            case CHAIN_BROKEN_EVENT_TYPE -> handleChainBroken(message);
            case ERA_RESOLUTION_COMPLETED_EVENT_TYPE -> handleEraResolutionCompleted(message);
            case PARADOX_CASCADED_EVENT_TYPE -> handleParadoxCascaded(message);
            default -> throw new IllegalStateException("Unreachable event type: " + envelope.eventType());
        }
    }

    private void handleOutcomeApplied(Message<Object> message) {
        var outcome = wireMapper.fromWire(read(message, OutcomeAppliedPayload.class));
        outcomeInboxRepository.save(outcome);
        contextRepository.resolveRevisionistActions(outcome.gameId(), outcome.eraNumber());
        contextRepository
                .resolveActivistDeclarations(outcome.gameId(), outcome.eraNumber())
                .forEach(applicationEventPublisher::publishEvent);
        completionChecker.tryComplete(outcome.gameId(), outcome.eraNumber());
    }

    private void handleChainCompleted(Message<Object> message) {
        var event = wireMapper.fromWire(read(message, ChainCompletedPayload.class));
        contextRepository.recordChainFact(
                event.gameId(), event.playerId(), event.chainId(), ScoreReason.CHAIN_COMPLETED, event.eraNumber());
    }

    private void handleChainBroken(Message<Object> message) {
        var event = wireMapper.fromWire(read(message, ChainBrokenPayload.class));
        contextRepository.recordChainFact(
                event.gameId(), event.targetPlayerId(), event.chainId(), ScoreReason.CHAIN_BROKEN, event.eraNumber());
    }

    private void handleParadoxCascaded(Message<Object> message) {
        var event = wireMapper.fromWire(read(message, ParadoxCascadedPayload.class));
        contextRepository.recordParadoxCascadeFact(
                event.gameId(),
                event.eraNumber(),
                event.paradoxId(),
                event.affectedEventId(),
                event.detonatedByPlayerIds());
    }

    private void handleEraResolutionCompleted(Message<Object> message) {
        var resolution = wireMapper.fromWire(read(message, EraResolutionCompletedPayload.class));
        contextRepository.saveEraResolutionCompleted(resolution);
        contextRepository.resolveRevisionistActions(resolution.gameId(), resolution.eraNumber());
        contextRepository
                .resolveActivistDeclarations(resolution.gameId(), resolution.eraNumber())
                .forEach(applicationEventPublisher::publishEvent);
        completionChecker.tryComplete(resolution.gameId(), resolution.eraNumber());
    }

    private <T> T read(Message<Object> message, Class<T> type) {
        return MessagePayloads.read(objectMapper, message, type);
    }
}
