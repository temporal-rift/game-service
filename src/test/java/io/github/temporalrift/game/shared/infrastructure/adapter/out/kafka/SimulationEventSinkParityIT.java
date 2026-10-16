package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import io.github.temporalrift.game.GameServiceApplication;
import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

/**
 * The in-memory sink must receive exactly what the Kafka adapter sends for the same event: the same envelope headers
 * and the same payload bytes.
 */
@GameServiceIntegrationTest
class SimulationEventSinkParityIT {

    private static final List<String> WIRE_HEADERS =
            List.of("eventType", "eventId", "aggregateId", "aggregateType", "gameId", "occurredAt", "version");

    @Autowired
    SessionEventPublisher kafkaBackedPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    KafkaContainer kafkaContainer;

    @Test
    void theSinkReceivesTheHeadersAndPayloadBytesKafkaReceives() throws Exception {
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-03-01T10:00:00.123456Z");
        var envelope = new DomainEventEnvelope<>(
                UUID.randomUUID(),
                lobbyId,
                "Lobby",
                gameId,
                occurredAt,
                1,
                new LobbyCreated(lobbyId, hostId, occurredAt));

        ConsumerRecord<String, byte[]> kafkaRecord;
        try (var consumer = rawConsumer()) {
            consumer.subscribe(List.of("game.events"));
            transactionTemplate.executeWithoutResult(_ -> kafkaBackedPublisher.publish(envelope));
            var received = new ArrayList<ConsumerRecord<String, byte[]>>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(received::add);
                assertThat(received).anyMatch(entry -> gameId.toString().equals(entry.key()));
            });
            kafkaRecord = received.stream()
                    .filter(entry -> gameId.toString().equals(entry.key()))
                    .findFirst()
                    .orElseThrow();
        }

        var sink = new CopyOnWriteArrayList<Map<String, Object>>();
        var host = new AnnotationConfigApplicationContext();
        host.registerBean("simulationEventSink", Consumer.class, () -> (Consumer<Map<String, Object>>) sink::add);
        host.registerBean(JwtDecoder.class, () -> token -> {
            throw new IllegalStateException("not used");
        });
        host.refresh();
        try (var application = new SpringApplicationBuilder(GameServiceApplication.class)
                        .parent(host)
                        .profiles("in-memory", "test")
                        .properties("game.simulation.enabled=true", "spring.main.banner-mode=off")
                        .run();
                host) {
            var inMemoryPublisher = application.getBean(SessionEventPublisher.class);
            application
                    .getBean(TransactionTemplate.class)
                    .executeWithoutResult(_ -> inMemoryPublisher.publish(envelope));
            await().atMost(Duration.ofSeconds(20))
                    .until(() ->
                            sink.stream().anyMatch(entry -> gameId.toString().equals(entry.get("key"))));
        }

        var sunk = sink.stream()
                .filter(entry -> gameId.toString().equals(entry.get("key")))
                .findFirst()
                .orElseThrow();
        assertThat((byte[]) sunk.get("payload")).isEqualTo(kafkaRecord.value());
        var sunkHeaders = new LinkedHashMap<String, String>();
        @SuppressWarnings("unchecked")
        var sunkHeaderMap = (Map<String, String>) sunk.get("headers");
        sunkHeaders.putAll(sunkHeaderMap);
        for (var name : WIRE_HEADERS) {
            var kafkaHeader = kafkaRecord.headers().lastHeader(name);
            assertThat(kafkaHeader).as("Kafka header %s", name).isNotNull();
            assertThat(sunkHeaders.get(name))
                    .as("header %s", name)
                    .isEqualTo(new String(kafkaHeader.value(), StandardCharsets.UTF_8));
        }
        assertThat(sunkHeaders.keySet()).containsExactlyInAnyOrderElementsOf(WIRE_HEADERS);
    }

    private KafkaConsumer<String, byte[]> rawConsumer() {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "sink-parity-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
