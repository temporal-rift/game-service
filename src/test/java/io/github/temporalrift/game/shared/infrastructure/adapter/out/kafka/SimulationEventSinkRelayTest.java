package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SimulationEventSinkRelayTest {

    private final List<Map<String, Object>> sink = new ArrayList<>();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SimulationEventSinkRelay relay = new SimulationEventSinkRelay(sink::add, mapper);

    @Test
    @SuppressWarnings("unchecked")
    void relayPassesAJdkOnlyRecordWithOnlyTheWireHeaders() {
        var event = new OutboundIntegrationEvent(
                OutboundIntegrationEventPublisher.GAME_EVENTS_CHANNEL,
                "GameStarted",
                "game-1",
                mapper.valueToTree(Map.of("a", 1)),
                Map.of("eventId", "e-1", "ignored", "x"));

        relay.relay(event);

        assertThat(sink).singleElement().satisfies(sent -> {
            assertThat(sent.keySet()).containsExactly("topic", "key", "headers", "payload");
            assertThat(sent.get("topic")).isEqualTo("game.events");
            assertThat(sent.get("key")).isEqualTo("game-1");
            assertThat((Map<String, String>) sent.get("headers"))
                    .containsEntry("eventType", "GameStarted")
                    .containsEntry("gameId", "game-1")
                    .containsEntry("eventId", "e-1")
                    .doesNotContainKey("ignored");
            assertThat(new String((byte[]) sent.get("payload"))).isEqualTo("{\"a\":1}");
        });
    }

    @Test
    void relayRejectsAnUnsupportedChannel() {
        var event = new OutboundIntegrationEvent("other", "GameStarted", "g", mapper.createObjectNode(), Map.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> relay.relay(event))
                .withMessageContaining("other");
        assertThat(sink).isEmpty();
    }
}
