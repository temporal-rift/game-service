package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ResolutionFailedPayload;
import io.github.temporalrift.game.session.application.saga.ResolutionFailedApplicationEvent;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.MessagePayloads;
import io.github.temporalrift.game.shared.ProcessedEventRepository;
import io.github.temporalrift.game.shared.TimelineEventEnvelope;

@Component
class ResolutionFailedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResolutionFailedKafkaConsumer.class);
    private static final String EVENT_TYPE = GeneratedChannelContract.RESOLUTION_FAILED_EVENT_TYPE;
    private static final String CONSUMER = "session.resolution-failed";

    private final ProcessedEventRepository processedEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    ResolutionFailedKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.session.resolution-failed")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(Message<Object> message) {
        var envelope = TimelineEventEnvelope.from(message);
        if (!shouldProcess(envelope, message)) {
            return;
        }

        var payload = MessagePayloads.read(objectMapper, message, ResolutionFailedPayload.class);
        // Validate before the mismatch check: a structurally invalid payload must still reach the dead-letter
        // topic for investigation, while a merely mis-routed one is discarded.
        validate(payload);
        if (!envelope.matchesGameId(payload.gameId())) {
            return;
        }
        applicationEventPublisher.publishEvent(new ResolutionFailedApplicationEvent(
                payload.gameId(), payload.eraNumber(), payload.affectedEventId(), payload.reason()));
    }

    private void validate(ResolutionFailedPayload payload) {
        if (payload.gameId() == null) {
            throw new IllegalArgumentException("ResolutionFailed payload is missing gameId");
        }
        if (payload.eraNumber() < 1) {
            throw new IllegalArgumentException("ResolutionFailed payload has an invalid eraNumber");
        }
    }

    private boolean shouldProcess(TimelineEventEnvelope envelope, Message<Object> message) {
        if (isMalformed(envelope, message)) {
            log.warn("Malformed record on timeline.events (missing eventId header or payload) — discarding");
            return false;
        }
        return EVENT_TYPE.equals(envelope.eventType()) && hasSupportedVersion(envelope) && claim(envelope);
    }

    private boolean isMalformed(TimelineEventEnvelope envelope, Message<Object> message) {
        return envelope.eventId() == null || MessagePayloads.isEmpty(message);
    }

    private boolean hasSupportedVersion(TimelineEventEnvelope envelope) {
        if (!envelope.hasVersion(DomainEventEnvelope.SCHEMA_VERSION_V1)) {
            log.warn(
                    "Unsupported {} envelope version {} for event {} — skipping",
                    EVENT_TYPE,
                    envelope.version(),
                    envelope.eventId());
            return false;
        }
        return true;
    }

    private boolean claim(TimelineEventEnvelope envelope) {
        var claimed = processedEventRepository.tryMarkProcessed(envelope.eventId(), CONSUMER);
        if (!claimed) {
            log.debug("Duplicate {} event {} ignored", EVENT_TYPE, envelope.eventId());
        }
        return claimed;
    }
}
