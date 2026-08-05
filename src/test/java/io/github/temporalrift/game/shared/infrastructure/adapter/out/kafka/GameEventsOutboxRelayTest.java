package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import tools.jackson.databind.node.JsonNodeFactory;

import io.github.temporalrift.game.shared.OutboundIntegrationEvent;
import io.github.temporalrift.game.shared.OutboundIntegrationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GameEventsOutboxRelayTest {

    @Mock
    StreamBridge streamBridge;

    @Test
    void relay_rejectsAnUnsupportedChannel() {
        var relay = new GameEventsOutboxRelay(streamBridge);
        var event = new OutboundIntegrationEvent(
                "someOtherChannel", "GameStarted", UUID.randomUUID().toString(), payload(), Map.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> relay.relay(event))
                .withMessageContaining("someOtherChannel");
        then(streamBridge).shouldHaveNoInteractions();
    }

    @Test
    void relay_sendsOnlyAllowListedHeadersPlusEventTypeAndGameId() {
        var relay = new GameEventsOutboxRelay(streamBridge);
        var gameId = UUID.randomUUID().toString();
        var eventId = UUID.randomUUID().toString();
        var headers = Map.<String, Object>of(
                "eventId", eventId, "occurredAt", Instant.now().toString(), "notAllowed", "should not be forwarded");
        var event = new OutboundIntegrationEvent(
                OutboundIntegrationEventPublisher.GAME_EVENTS_CHANNEL, "GameStarted", gameId, payload(), headers);
        given(streamBridge.send(eq("game-events-out"), any())).willReturn(true);

        relay.relay(event);

        var messageCaptor = ArgumentCaptor.forClass(Message.class);
        then(streamBridge).should().send(eq("game-events-out"), messageCaptor.capture());
        var sentHeaders = messageCaptor.getValue().getHeaders();
        assertThat(sentHeaders)
                .containsEntry("eventType", "GameStarted")
                .containsEntry("gameId", gameId)
                .containsEntry("eventId", eventId)
                .doesNotContainKey("notAllowed");
    }

    @Test
    void relay_throwsWhenStreamBridgeRejectsTheSend() {
        var relay = new GameEventsOutboxRelay(streamBridge);
        var event = new OutboundIntegrationEvent(
                OutboundIntegrationEventPublisher.GAME_EVENTS_CHANNEL,
                "GameStarted",
                UUID.randomUUID().toString(),
                payload(),
                Map.of());
        given(streamBridge.send(eq("game-events-out"), any())).willReturn(false);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> relay.relay(event))
                .withMessageContaining("GameStarted");
    }

    private static tools.jackson.databind.JsonNode payload() {
        return JsonNodeFactory.instance.objectNode();
    }
}
