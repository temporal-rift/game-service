package io.github.temporalrift.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.scoring.domain.event.ChainCompleted;
import io.github.temporalrift.game.session.domain.event.ParadoxCascaded;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;
import io.github.temporalrift.game.shared.InboundEnvelope;

/**
 * Regression coverage for the consumer-group collision (issue #70): both {@code timeline.events}
 * listeners used to inherit the default {@code game-service} group, so Kafka split the topic's
 * partitions between them and each listener silently missed the records assigned to the other.
 * With distinct groups, one record published once must be processed by both consumers.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TimelineEventsConsumerGroupsIT {

    private static final String TOPIC = "timeline.events";

    @Autowired
    KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    GameRepository gameRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void bothTimelineListeners_consumeFromTheSamePartition() {
        var gameId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(
                _ -> gameRepository.save(new Game(gameId, UUID.randomUUID(), List.of())));

        var paradoxEnvelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.ParadoxCascaded",
                gameId,
                "FutureEvent",
                gameId,
                Instant.now(),
                1,
                new ParadoxCascaded(gameId, 1, UUID.randomUUID(), UUID.randomUUID(), List.of()));
        var chainEnvelope = new InboundEnvelope(
                UUID.randomUUID(),
                "timeline.ChainCompleted",
                gameId,
                "WeaverChain",
                gameId,
                Instant.now(),
                1,
                new ChainCompleted(gameId, 1, UUID.randomUUID(), UUID.randomUUID(), List.of()));

        // Same key — both records land in the same partition, which is exactly the case the shared
        // consumer group could not deliver to both listeners.
        kafkaTemplate.send(TOPIC, gameId.toString(), paradoxEnvelope);
        kafkaTemplate.send(TOPIC, gameId.toString(), chainEnvelope);

        awaitProcessed(paradoxEnvelope.eventId(), "session.paradox-cascaded");
        awaitProcessed(chainEnvelope.eventId(), "scoring.timeline-events");
    }

    private void awaitProcessed(UUID eventId, String consumer) {
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM processed_events WHERE event_id = ? AND consumer = ?",
                                Integer.class,
                                eventId,
                                consumer))
                        .isEqualTo(1));
    }
}
