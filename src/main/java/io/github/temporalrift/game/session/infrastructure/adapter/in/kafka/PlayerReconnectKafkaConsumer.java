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

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.game.session.application.saga.PlayerReconnectedApplicationEvent;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@Component
class PlayerReconnectKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectKafkaConsumer.class);
    private static final String EVENT_TYPE = "session.PlayerReconnected";
    private static final String CONSUMER = "session.player-reconnect";

    private final ProcessedEventRepository processedEventRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    PlayerReconnectKafkaConsumer(
            ProcessedEventRepository processedEventRepository,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "game.commands")
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(EventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            return;
        }
        if (!processedEventRepository.tryMarkProcessed(envelope.eventId(), CONSUMER)) {
            log.debug("Duplicate {} event {} ignored", EVENT_TYPE, envelope.eventId());
            return;
        }

        var payload = objectMapper.convertValue(envelope.payload(), PlayerReconnectedPayload.class);
        log.debug("Player reconnect signal received for game {} player {}", payload.gameId(), payload.playerId());
        applicationEventPublisher.publishEvent(
                new PlayerReconnectedApplicationEvent(payload.gameId(), payload.playerId()));
    }

    record PlayerReconnectedPayload(UUID gameId, UUID playerId) {}
}
