package io.github.temporalrift.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.JsonKafkaHeaderMapper;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraResolutionCompletedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.EraTerminalResolution;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.OutcomeAppliedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxCascadedPayload;
import io.github.temporalrift.asyncapi.timelineevents.GeneratedChannelContract.ParadoxResolutionPhaseStartedPayload;
import io.github.temporalrift.game.session.domain.game.Game;
import io.github.temporalrift.game.session.domain.port.out.GameRepository;

/**
 * End-to-end coverage of the published {@code timeline.events} wire contract: envelope metadata in Kafka headers,
 * only the typed payload in the record body, and plain AsyncAPI message names as the routing discriminator.
 *
 * <p>Also the regression guard for the consumer-group collision (issue #70): both {@code timeline.events} listeners
 * used to inherit the default {@code game-service} group, so Kafka split the topic's partitions between them and
 * each listener silently missed the records assigned to the other. Every logical timeline-event consumer now has its
 * own group and receives the same partition.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TimelineEventsConsumerGroupsIT {

    private static final String TOPIC = "timeline.events";
    private static final int ERA_NUMBER = 1;
    private static final JsonKafkaHeaderMapper HEADER_MAPPER = new JsonKafkaHeaderMapper();

    @Autowired
    KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    GameRepository gameRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void publishedWireRecords_reachEveryOwningConsumerGroup() {
        var gameId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(
                _ -> gameRepository.save(new Game(gameId, UUID.randomUUID(), List.of())));

        var cascadedEventId = UUID.randomUUID();
        var resolution = event(
                gameId,
                "EraResolutionCompleted",
                "Game",
                new EraResolutionCompletedPayload(
                        gameId, ERA_NUMBER, List.of(new EraTerminalResolution(cascadedEventId, 0, "CASCADED", null))));
        var outcome = event(
                gameId,
                "OutcomeApplied",
                "FutureEvent",
                new OutcomeAppliedPayload(gameId, ERA_NUMBER, UUID.randomUUID(), UUID.randomUUID(), List.of()));
        var phaseStarted = event(
                gameId,
                "ParadoxResolutionPhaseStarted",
                "ParadoxResolutionPhase",
                new ParadoxResolutionPhaseStartedPayload(gameId, ERA_NUMBER, List.of(UUID.randomUUID()), 30));
        var paradoxId = UUID.randomUUID();
        var cascade = event(
                gameId,
                "ParadoxCascaded",
                "FutureEvent",
                new ParadoxCascadedPayload(
                        gameId, ERA_NUMBER, paradoxId, cascadedEventId, List.of(), List.of(UUID.randomUUID())));

        // Same key — all records land in the same partition, which is exactly the case a shared
        // consumer group could not deliver to every listener. Publish in resolution order so the barrier
        // closes a phase that is actually open, rather than arriving before anything opened one.
        send(gameId, phaseStarted);
        send(gameId, resolution);
        // The same cascade eventId twice. Cascade facts are inserted with a fresh primary key and no unique
        // constraint, so only the eventId claim stops a redelivery from recording a second fact.
        send(gameId, cascade);
        send(gameId, cascade);
        // Published last on the same key: one partition, consumed in order, so the scoring group having
        // claimed this record proves it already handled both cascade deliveries.
        send(gameId, outcome);

        awaitProcessed(resolution, "session.era-resolution-completed");
        awaitProcessed(resolution, "scoring.timeline-events");
        awaitProcessed(resolution, "action.paradox-resolution-phase");
        awaitProcessed(phaseStarted, "action.paradox-resolution-phase");
        awaitProcessed(cascade, "scoring.timeline-events");
        awaitProcessed(outcome, "scoring.timeline-events");

        assertThat(cascadeFactCount(gameId, paradoxId)).isEqualTo(1);
        assertThat(phaseStatus(gameId)).isEqualTo("CLOSED");
    }

    /**
     * Publishes with an explicit {@link ProducerRecord} so the payload reaches the broker as the plain serialized
     * event body. {@code KafkaTemplate.send(Message)} would first run it through the configured JSON
     * <em>message converter</em> and then through the JSON <em>value serializer</em>, double-encoding the body —
     * which is not what {@code timeline-service}'s relay produces. Headers go through the same
     * {@link JsonKafkaHeaderMapper} the producer side uses, so {@code occurredAt} and {@code version} travel with
     * their real types.
     */
    private void send(UUID gameId, Message<Object> event) {
        var producerRecord = new ProducerRecord<Object, Object>(TOPIC, null, gameId.toString(), event.getPayload());
        HEADER_MAPPER.fromHeaders(event.getHeaders(), producerRecord.headers());
        kafkaTemplate.send(producerRecord);
    }

    private void awaitProcessed(Message<Object> event, String consumer) {
        var eventId = UUID.fromString((String) event.getHeaders().get("eventId"));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM processed_events WHERE event_id = ? AND consumer = ?",
                                Integer.class,
                                eventId,
                                consumer))
                        .isEqualTo(1));
    }

    /** Proves the barrier closed the phase the earlier {@code ParadoxResolutionPhaseStarted} record opened. */
    private String phaseStatus(UUID gameId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM paradox_resolution_phase WHERE game_id = ? AND era_number = ?",
                String.class,
                gameId,
                ERA_NUMBER);
    }

    private Integer cascadeFactCount(UUID gameId, UUID paradoxId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scoring_context_paradox_cascade_fact WHERE game_id = ? AND paradox_id = ?",
                Integer.class,
                gameId,
                paradoxId);
    }

    /** The published wire shape: envelope metadata in the headers, only the typed payload in the body. */
    private static Message<Object> event(UUID gameId, String eventType, String aggregateType, Object payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader("eventType", eventType)
                .setHeader("eventId", UUID.randomUUID().toString())
                .setHeader("aggregateId", gameId.toString())
                .setHeader("aggregateType", aggregateType)
                .setHeader("gameId", gameId.toString())
                .setHeader("occurredAt", Instant.now())
                .setHeader("version", 1)
                .build();
    }
}
