package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.AdjustedBandsPublishedPayload;
import io.github.temporalrift.game.session.application.saga.BandsPublishedApplicationEvent;
import io.github.temporalrift.game.shared.domain.port.out.ProcessedEventRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.MessagePayloads;
import io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka.TimelineEventEnvelope;

@Component
class BandsPublishedKafkaConsumer {

    private static final String EVENT_TYPE = GeneratedChannelContract.ADJUSTED_BANDS_PUBLISHED_EVENT_TYPE;
    private static final String CONSUMER = "session.bands-published";

    private final TimelineEventClaim eventClaim;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    BandsPublishedKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper) {
        this.eventClaim = new TimelineEventClaim(processedEventRepository, EVENT_TYPE, CONSUMER);
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "timeline.events", groupId = "game-service.session.bands-published")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(Message<Object> message) {
        var envelope = TimelineEventEnvelope.from(message);
        if (!eventClaim.tryClaim(envelope, message)) {
            return;
        }

        var payload = MessagePayloads.read(objectMapper, message, AdjustedBandsPublishedPayload.class);
        // Validate before the mismatch check: a structurally invalid payload must still reach the dead-letter
        // topic for investigation, while a merely mis-routed one is discarded.
        validate(payload);
        if (!envelope.matchesGameId(payload.gameId())) {
            return;
        }
        applicationEventPublisher.publishEvent(
                new BandsPublishedApplicationEvent(payload.gameId(), payload.eraNumber()));
    }

    private void validate(AdjustedBandsPublishedPayload payload) {
        if (payload.gameId() == null) {
            throw new IllegalArgumentException("BandsPublished payload is missing gameId");
        }
        if (payload.eraNumber() < 1) {
            throw new IllegalArgumentException("BandsPublished payload has an invalid eraNumber");
        }
    }
}
