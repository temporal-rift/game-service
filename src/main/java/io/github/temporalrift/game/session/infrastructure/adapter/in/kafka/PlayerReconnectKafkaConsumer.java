package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.session.application.saga.PlayerReconnectedApplicationEvent;

@Component
class PlayerReconnectKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectKafkaConsumer.class);
    private static final String EVENT_TYPE = "session.PlayerReconnected";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    PlayerReconnectKafkaConsumer(ApplicationEventPublisher applicationEventPublisher, ObjectMapper objectMapper) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "game.commands")
    public void handle(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
        var payload = objectMapper.convertValue(envelope.payload(), PlayerReconnectedPayload.class);
        log.debug("Player reconnect signal received for game {} player {}", payload.gameId(), payload.playerId());
        applicationEventPublisher.publishEvent(
                new PlayerReconnectedApplicationEvent(payload.gameId(), payload.playerId()));
    }

    private record PlayerReconnectedPayload(UUID gameId, UUID playerId) {}
}
