package io.github.temporalrift.game.action.infrastructure.adapter.in.kafka;

import static io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ERA_RESOLUTION_COMPLETED_EVENT_TYPE;
import static io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.PARADOX_RESOLUTION_PHASE_STARTED_EVENT_TYPE;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxResolutionPhaseStartedPayload;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.MessagePayloads;
import io.github.temporalrift.game.shared.ProcessedEventRepository;
import io.github.temporalrift.game.shared.TimelineEventEnvelope;

@Component
class ParadoxResolutionPhaseKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParadoxResolutionPhaseKafkaConsumer.class);
    private static final String CONSUMER = "action.paradox-resolution-phase";

    private final ProcessedEventRepository processedEventRepository;
    private final ParadoxResolutionPhaseRepository phaseRepository;
    private final ObjectMapper objectMapper;

    ParadoxResolutionPhaseKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            ParadoxResolutionPhaseRepository phaseRepository,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.phaseRepository = phaseRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.action.paradox-resolution-phase")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(Message<Object> message) {
        var envelope = TimelineEventEnvelope.from(message);
        if (isMalformed(envelope, message)) {
            log.warn("Malformed record on timeline.events for paradox phase tracking — discarding");
            return;
        }
        if (!PARADOX_RESOLUTION_PHASE_STARTED_EVENT_TYPE.equals(envelope.eventType())
                && !ERA_RESOLUTION_COMPLETED_EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
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
            case PARADOX_RESOLUTION_PHASE_STARTED_EVENT_TYPE -> openPhase(envelope, message);
            case ERA_RESOLUTION_COMPLETED_EVENT_TYPE -> closePhase(envelope, message);
            default -> throw new IllegalStateException("Unreachable event type: " + envelope.eventType());
        }
    }

    private boolean isMalformed(TimelineEventEnvelope envelope, Message<Object> message) {
        return envelope.eventId() == null
                || envelope.aggregateId() == null
                || envelope.gameId() == null
                || envelope.occurredAt() == null
                || message.getPayload() == null;
    }

    private void openPhase(TimelineEventEnvelope envelope, Message<Object> message) {
        var started = MessagePayloads.read(objectMapper, message, ParadoxResolutionPhaseStartedPayload.class);
        if (!envelope.matchesGameId(started.gameId())) {
            return;
        }
        if (phaseRepository
                .findByGameIdAndEraNumber(started.gameId(), started.eraNumber())
                .isPresent()) {
            return;
        }
        phaseRepository.save(new ParadoxResolutionPhase(
                envelope.eventId(),
                started.gameId(),
                started.eraNumber(),
                envelope.occurredAt().plusSeconds(started.timerSeconds())));
    }

    private void closePhase(TimelineEventEnvelope envelope, Message<Object> message) {
        var completed = MessagePayloads.read(objectMapper, message, EraResolutionCompletedPayload.class);
        if (!envelope.matchesGameId(completed.gameId())) {
            return;
        }
        phaseRepository
                .findByGameIdAndEraNumberWithLock(completed.gameId(), completed.eraNumber())
                .ifPresent(phase -> {
                    phase.close();
                    phaseRepository.save(phase);
                });
    }
}
