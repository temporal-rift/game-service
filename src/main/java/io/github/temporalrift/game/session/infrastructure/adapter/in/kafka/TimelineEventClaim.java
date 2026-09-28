package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;

import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;
import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.MessagePayloads;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.TimelineEventEnvelope;

/** Claims accepted records in the listener's transaction so failed payload handling rolls back the claim. */
final class TimelineEventClaim {

    private static final Logger log = LoggerFactory.getLogger(TimelineEventClaim.class);

    private final ProcessedEventRepository processedEventRepository;
    private final String eventType;
    private final String consumer;

    TimelineEventClaim(ProcessedEventRepository processedEventRepository, String eventType, String consumer) {
        this.processedEventRepository = processedEventRepository;
        this.eventType = eventType;
        this.consumer = consumer;
    }

    boolean tryClaim(TimelineEventEnvelope envelope, Message<?> message) {
        if (envelope.eventId() == null || MessagePayloads.isEmpty(message)) {
            log.warn("Malformed record on timeline.events (missing eventId header or payload) — discarding");
            return false;
        }
        if (!eventType.equals(envelope.eventType())) {
            return false;
        }
        if (!envelope.hasVersion(DomainEventEnvelope.SCHEMA_VERSION_V1)) {
            log.warn(
                    "Unsupported {} envelope version {} for event {} — skipping",
                    eventType,
                    envelope.version(),
                    envelope.eventId());
            return false;
        }
        if (!processedEventRepository.tryMarkProcessed(envelope.eventId(), consumer)) {
            log.debug("Duplicate {} event {} ignored", eventType, envelope.eventId());
            return false;
        }
        return true;
    }
}
