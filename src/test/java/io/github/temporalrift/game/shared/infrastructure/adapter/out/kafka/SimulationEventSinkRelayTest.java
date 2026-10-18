package io.github.temporalrift.game.shared.infrastructure.adapter.out.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryTransactionManager;

class SimulationEventSinkRelayTest {

    private final List<Map<String, Object>> sink = Collections.synchronizedList(new ArrayList<>());
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final InMemoryDatabase database = new InMemoryDatabase(Duration.ofSeconds(5));
    private final TransactionTemplate transactions = new TransactionTemplate(new InMemoryTransactionManager(database));
    private final SimulationEventSinkRelay relay = new SimulationEventSinkRelay(sink::add, mapper, database);

    private OutboundIntegrationEvent event(String key, String eventId) {
        return new OutboundIntegrationEvent(
                OutboundIntegrationEventPublisher.GAME_EVENTS_CHANNEL,
                "GameStarted",
                key,
                mapper.valueToTree(Map.of("a", 1)),
                Map.of("eventId", eventId, "ignored", "x"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void relayPassesAJdkOnlyRecordWithOnlyTheWireHeadersWhenItsTransactionCommits() {
        transactions.executeWithoutResult(status -> {
            relay.relay(event("game-1", "e-1"));
            assertThat(sink).isEmpty();
        });

        assertThat(sink).singleElement().satisfies(sent -> {
            assertThat(sent.keySet()).containsExactly("topic", "key", "headers", "payload");
            assertThat(sent).containsEntry("topic", "game.events").containsEntry("key", "game-1");
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
                .isThrownBy(() -> transactions.executeWithoutResult(status -> relay.relay(event)))
                .withMessageContaining("other");
        assertThat(sink).isEmpty();
    }

    @Test
    void aRolledBackTransactionRelaysNothing() {
        transactions.executeWithoutResult(status -> {
            relay.relay(event("game-1", "e-1"));
            status.setRollbackOnly();
        });

        assertThat(sink).isEmpty();
    }

    @Test
    void recordsOfConcurrentCommitsReachTheSinkOneAtATime() throws Exception {
        var threads = 8;
        var inSink = new AtomicInteger();
        var overlapped = new AtomicInteger();
        var commitOrder = Collections.synchronizedList(new ArrayList<String>());
        var slowSink = new SimulationEventSinkRelay(
                message -> {
                    if (inSink.incrementAndGet() > 1) {
                        overlapped.incrementAndGet();
                    }
                    commitOrder.add((String) message.get("key"));
                    holdBriefly();
                    inSink.decrementAndGet();
                },
                mapper,
                database);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            for (var thread = 0; thread < threads; thread++) {
                var key = "game-" + thread;
                pool.submit(() -> {
                    start.await();
                    transactions.executeWithoutResult(status -> slowSink.relay(event(key, key)));
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(commitOrder).hasSize(threads).doesNotHaveDuplicates();
        assertThat(overlapped).hasValue(0);
    }

    private static void holdBriefly() {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
    }
}
