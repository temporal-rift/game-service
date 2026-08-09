package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.application.saga.ResolutionFailedApplicationEvent;
import io.github.temporalrift.game.shared.DomainEventEnvelope;
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@Component
class ResolutionFailedKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResolutionFailedKafkaConsumer.class);
    private static final String EVENT_TYPE = "timeline.ResolutionFailed";
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
    public void handle(InboundEnvelope envelope) {
        if (!shouldProcess(envelope)) {
            return;
        }

        var payload = objectMapper.convertValue(envelope.payload(), ResolutionFailedPayload.class);
        validate(payload);
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

    private boolean shouldProcess(InboundEnvelope envelope) {
        if (isMalformed(envelope)) {
            log.warn("Malformed envelope on timeline.events (missing eventId or payload) — discarding");
            return false;
        }
        return EVENT_TYPE.equals(envelope.eventType()) && hasSupportedVersion(envelope) && claim(envelope);
    }

    private boolean isMalformed(InboundEnvelope envelope) {
        return envelope.eventId() == null || envelope.payload() == null;
    }

    private boolean hasSupportedVersion(InboundEnvelope envelope) {
        if (envelope.version() != DomainEventEnvelope.SCHEMA_VERSION_V1) {
            log.warn(
                    "Unsupported {} envelope version {} for event {} — skipping",
                    EVENT_TYPE,
                    envelope.version(),
                    envelope.eventId());
            return false;
        }
        return true;
    }

    private boolean claim(InboundEnvelope envelope) {
        var claimed = processedEventRepository.tryMarkProcessed(envelope.eventId(), CONSUMER);
        if (!claimed) {
            log.debug("Duplicate {} event {} ignored", EVENT_TYPE, envelope.eventId());
        }
        return claimed;
    }

    record ResolutionFailedPayload(UUID gameId, int eraNumber, UUID affectedEventId, String reason) {}
}
