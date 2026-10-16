package io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class SimulationEventDeliveryTest {

    record Command(String name) {}

    static class Listeners {

        final List<String> handled = new ArrayList<>();
        final AtomicInteger failures = new AtomicInteger();

        @KafkaListener(topics = "timeline.events", groupId = "group-b")
        public void second(Message<Object> message) {
            handled.add("b:" + message.getPayload());
        }

        @KafkaListener(topics = "timeline.events", groupId = "group-a")
        public void first(Message<Object> message) {
            handled.add("a:" + message.getHeaders().get("eventId", String.class));
        }

        @KafkaListener(topics = "timeline.events", groupId = "group-failing")
        public void failing(Message<Object> message) {
            failures.incrementAndGet();
            throw new IllegalStateException("handler failed");
        }

        @KafkaListener(topics = "game.commands", groupId = "group-typed")
        public void typed(Command command) {
            handled.add("typed:" + command.name());
        }

        @KafkaListener(topics = "broken.topic", groupId = "group-checked")
        public void checked(Message<Object> message) throws Exception {
            throw new Exception("checked failure");
        }
    }

    private SimulationEventDelivery delivery;
    private Listeners listeners;

    @BeforeEach
    void setUp() {
        listeners = new Listeners();
        var registry = new KafkaListenerRegistry(new MockEnvironment());
        registry.postProcessAfterInitialization(listeners, "listeners");
        delivery = new SimulationEventDelivery(
                registry,
                new ByteArrayJacksonJsonMessageConverter(JsonMapper.builder().build()));
    }

    @Test
    void everyGroupOfTheTopicHandlesTheRecordOnceInGroupOrderAndAFailureDoesNotStopTheOthers() {
        var delivered = message("timeline.events", "{\"x\":1}");

        assertThatThrownBy(() -> delivery.accept(delivered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("handler failed");

        assertThat(listeners.handled).hasSize(2).first().isEqualTo("a:11111111-1111-1111-1111-111111111111");
        assertThat(listeners.handled.get(1)).startsWith("b:");
        assertThat(listeners.failures).hasValue(1);
        assertThat(delivery.positions())
                .filteredOn(position -> position.topic().equals("timeline.events"))
                .extracting(
                        SimulationEventDelivery.ConsumerPosition::groupId,
                        SimulationEventDelivery.ConsumerPosition::nextOffset)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("group-a", 1L),
                        org.assertj.core.groups.Tuple.tuple("group-b", 1L),
                        org.assertj.core.groups.Tuple.tuple("group-failing", 0L));
    }

    @Test
    void aTypedListenerReceivesTheConvertedPayload() {
        delivery.accept(message("game.commands", "{\"name\":\"reconnect\"}"));

        assertThat(listeners.handled).containsExactly("typed:reconnect");
    }

    @Test
    void aTopicWithoutListenersIsIgnored() {
        delivery.accept(message("game.events", "{}"));

        assertThat(listeners.handled).isEmpty();
    }

    @Test
    void aCheckedListenerFailureIsWrapped() {
        var broken = message("broken.topic", "{}");

        assertThatThrownBy(() -> delivery.accept(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("checked failure");
    }

    @Test
    void recordEntriesOfTheWrongTypeAreRejectedAsIllegalArguments() {
        var wrongPayload = new LinkedHashMap<String, Object>(message("timeline.events", "{}"));
        wrongPayload.put("payload", "text");
        var wrongHeaders = new LinkedHashMap<String, Object>(message("timeline.events", "{}"));
        wrongHeaders.put("headers", Map.of("eventId", 1));
        var wrongTopic = new LinkedHashMap<String, Object>(message("timeline.events", "{}"));
        wrongTopic.put("topic", 7);
        var notAMap = new LinkedHashMap<String, Object>(message("timeline.events", "{}"));
        notAMap.put("headers", "headers");

        assertThatThrownBy(() -> delivery.accept(wrongPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
        assertThatThrownBy(() -> delivery.accept(wrongHeaders))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headers");
        assertThatThrownBy(() -> delivery.accept(notAMap))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headers");
        assertThatThrownBy(() -> delivery.accept(wrongTopic))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
        assertThat(listeners.handled).isEmpty();
    }

    @Test
    void aMalformedRecordNamesTheMissingKey() {
        var malformed = new LinkedHashMap<String, Object>(message("timeline.events", "{\"x\":1}"));
        malformed.remove("headers");

        assertThatThrownBy(() -> delivery.accept(malformed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("headers");
    }

    private static Map<String, Object> message(String topic, String payload) {
        var headers = new LinkedHashMap<String, String>();
        headers.put("eventId", "11111111-1111-1111-1111-111111111111");
        var message = new LinkedHashMap<String, Object>();
        message.put("topic", topic);
        message.put("key", "game");
        message.put("headers", headers);
        message.put("payload", payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
