package io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.JsonKafkaHeaderMapper;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 * The inbound edge of the in-memory runtime: takes a record a host would have published to Kafka and runs, on the
 * calling thread, exactly the handling of every {@code @KafkaListener} subscribed to its topic, once per consumer
 * group. Record shape: {@code topic}, {@code key} (strings), {@code headers} ({@code Map<String, String>}) and
 * {@code payload} ({@code byte[]}). A failing group does not stop the others; the first failure is rethrown after all
 * groups ran.
 */
public class SimulationEventDelivery implements Consumer<Map<String, Object>> {

    private static final int PARTITION = 0;
    // Headers are encoded as a producer would, so the consumer-side mapper reads them back as strings.
    private static final JsonKafkaHeaderMapper HEADER_MAPPER = new JsonKafkaHeaderMapper();

    private final KafkaListenerRegistry registry;
    private final RecordMessageConverter converter;
    private final Map<GroupTopic, AtomicLong> handled = new ConcurrentHashMap<>();

    SimulationEventDelivery(KafkaListenerRegistry registry, RecordMessageConverter converter) {
        this.registry = registry;
        this.converter = converter;
    }

    @Override
    public void accept(Map<String, Object> delivered) {
        var topic = text(delivered, "topic");
        var listeners = registry.listenersOf(topic);
        RuntimeException failure = null;
        for (var listener : listeners) {
            try {
                deliver(listener, delivered);
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** How many records each consumer group has handled per topic and partition, as committed offsets would say. */
    public List<ConsumerPosition> positions() {
        var positions = new ArrayList<ConsumerPosition>();
        registry.listeners()
                .forEach(listener -> positions.add(new ConsumerPosition(
                        listener.groupId(),
                        listener.topic(),
                        PARTITION,
                        handled.getOrDefault(new GroupTopic(listener.groupId(), listener.topic()), new AtomicLong())
                                .get())));
        positions.sort(Comparator.comparing(ConsumerPosition::groupId)
                .thenComparing(ConsumerPosition::topic)
                .thenComparingInt(ConsumerPosition::partition));
        return List.copyOf(positions);
    }

    private void deliver(KafkaListenerRegistry.Listener listener, Map<String, Object> delivered) {
        var counter =
                handled.computeIfAbsent(new GroupTopic(listener.groupId(), listener.topic()), key -> new AtomicLong());
        var message = converter.toMessage(
                consumerRecord(listener, delivered, counter.get()), null, null, payloadType(listener));
        listener.invoke(Message.class.isAssignableFrom(listener.parameterType()) ? message : message.getPayload());
        counter.incrementAndGet();
    }

    private static ConsumerRecord<String, byte[]> consumerRecord(
            KafkaListenerRegistry.Listener listener, Map<String, Object> delivered, long offset) {
        var consumed = new ConsumerRecord<>(
                listener.topic(), PARTITION, offset, text(delivered, "key"), (byte[]) required(delivered, "payload"));
        HEADER_MAPPER.fromHeaders(
                new MessageHeaders(new LinkedHashMap<String, Object>(headers(delivered))), consumed.headers());
        return consumed;
    }

    /** The type the container converts the record value to: the declared payload, or raw bytes for Object. */
    private static Type payloadType(KafkaListenerRegistry.Listener listener) {
        var declared = listener.method().getGenericParameterTypes()[0];
        if (declared instanceof ParameterizedType parameterized && Message.class.equals(parameterized.getRawType())) {
            return parameterized.getActualTypeArguments()[0];
        }
        return declared;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headers(Map<String, Object> delivered) {
        return (Map<String, String>) required(delivered, "headers");
    }

    private static String text(Map<String, Object> delivered, String name) {
        return (String) required(delivered, name);
    }

    private static Object required(Map<String, Object> delivered, String name) {
        var value = delivered.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Simulation record lacks '" + name + "'");
        }
        return value;
    }

    /** A consumer group's progress on one partition: the offset of the next record it will handle. */
    public record ConsumerPosition(String groupId, String topic, int partition, long nextOffset) {}

    private record GroupTopic(String groupId, String topic) {}
}
