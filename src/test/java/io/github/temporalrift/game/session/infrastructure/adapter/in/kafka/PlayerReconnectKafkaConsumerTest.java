package io.github.temporalrift.game.session.infrastructure.adapter.in.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.application.saga.PlayerReconnectedApplicationEvent;
import io.github.temporalrift.game.shared.InboundEnvelope;
import io.github.temporalrift.game.shared.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class PlayerReconnectKafkaConsumerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final Instant OCCURRED_AT = Instant.parse("2026-06-18T00:00:00Z");

    @Mock
    ProcessedEventRepository processedEventRepository;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    PlayerReconnectKafkaConsumer consumer;

    @Test
    @DisplayName("player reconnected — marks event and publishes typed Spring event")
    void handle_playerReconnected_marksEventAndPublishesTypedEvent() {
        // given
        var envelope = envelopeFor("session.PlayerReconnected");
        var payload = new PlayerReconnectKafkaConsumer.PlayerReconnectedPayload(GAME_ID, PLAYER_ID);
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "session.player-reconnect"))
                .willReturn(true);
        given(objectMapper.convertValue(any(), eq(PlayerReconnectKafkaConsumer.PlayerReconnectedPayload.class)))
                .willReturn(payload);
        var captor = ArgumentCaptor.forClass(Object.class);

        // when
        consumer.handle(envelope);

        // then
        then(applicationEventPublisher).should().publishEvent(captor.capture());
        var event = (PlayerReconnectedApplicationEvent) captor.getValue();
        org.assertj.core.api.Assertions.assertThat(event.gameId()).isEqualTo(GAME_ID);
        org.assertj.core.api.Assertions.assertThat(event.playerId()).isEqualTo(PLAYER_ID);
    }

    @Test
    @DisplayName("duplicate eventId — ignored without publishing typed Spring event")
    void handle_duplicateEventId_ignored() {
        // given
        var envelope = envelopeFor("session.PlayerReconnected");
        given(processedEventRepository.tryMarkProcessed(envelope.eventId(), "session.player-reconnect"))
                .willReturn(false);

        // when
        consumer.handle(envelope);

        // then
        then(objectMapper)
                .should(never())
                .convertValue(any(), eq(PlayerReconnectKafkaConsumer.PlayerReconnectedPayload.class));
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("unrelated event type — ignored without marking processed")
    void handle_wrongEventType_ignored() {
        // given
        var envelope = envelopeFor("session.Other");

        // when
        consumer.handle(envelope);

        // then
        then(processedEventRepository).should(never()).tryMarkProcessed(any(), any());
        then(applicationEventPublisher).should(never()).publishEvent(any());
    }

    private static InboundEnvelope envelopeFor(String eventType) {
        return new InboundEnvelope(UUID.randomUUID(), eventType, GAME_ID, "Game", GAME_ID, OCCURRED_AT, 1, "");
    }
}
