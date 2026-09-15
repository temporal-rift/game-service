package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;

import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.session.domain.event.LobbyCreated;
import io.github.temporalrift.game.session.domain.lobby.Lobby;
import io.github.temporalrift.game.session.domain.lobby.LobbyConfig;
import io.github.temporalrift.game.session.domain.lobby.LobbyStatus;
import io.github.temporalrift.game.session.domain.port.out.LobbyRepository;
import io.github.temporalrift.game.session.domain.port.out.SessionEventPublisher;
import io.github.temporalrift.game.shared.domain.messaging.DomainEventEnvelope;

/**
 * Regression guard for issue #194: the Spring Cloud Stream Kafka binder failed to resolve the Testcontainers
 * broker address, so no test ever proved a {@link GameEventsOutboxRelay} send reaches a real broker rather than
 * just the outbox table {@code SessionEventPublisherAdapterIT} already covers.
 */
@GameServiceIntegrationTest
class GameEventsOutboxRelayIT {

    private static final String TOPIC = "game.events";

    @Autowired
    SessionEventPublisher sessionEventPublisher;

    @Autowired
    LobbyRepository lobbyRepository;

    @Autowired
    Clock clock;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    KafkaContainer kafkaContainer;

    @Test
    void relayedEvent_reachesTheRealGameEventsTopic() {
        var lobbyId = UUID.randomUUID();
        var gameId = UUID.randomUUID();
        var hostId = UUID.randomUUID();
        var lobby = Lobby.reconstitute(
                lobbyId,
                gameId,
                hostId,
                new ArrayList<>(),
                LobbyStatus.WAITING,
                new LobbyConfig("RELAY01", 2, 5, clock));
        var envelope = DomainEventEnvelope.create(
                lobbyId, "Lobby", gameId, 1, new LobbyCreated(lobbyId, hostId, Instant.now(clock)), clock);

        var received = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = newRawConsumer()) {
            consumer.subscribe(List.of(TOPIC));

            transactionTemplate.executeWithoutResult(_ -> {
                lobbyRepository.save(lobby);
                sessionEventPublisher.publish(envelope);
            });

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(received::add);
                assertThat(received).anySatisfy(record -> {
                    assertThat(record.key()).isEqualTo(gameId.toString());
                    assertThat(record.value()).contains(hostId.toString());
                });
            });
        }
    }

    private KafkaConsumer<String, String> newRawConsumer() {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "game-events-relay-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
