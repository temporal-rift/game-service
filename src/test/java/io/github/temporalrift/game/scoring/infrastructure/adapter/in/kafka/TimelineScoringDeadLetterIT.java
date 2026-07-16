package io.github.temporalrift.game.scoring.infrastructure.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import io.github.temporalrift.game.TestcontainersConfiguration;

/**
 * Verifies that the configured dead-letter recoverer parks an unprocessable record on {@code game.dlq}
 * rather than dropping it. The recoverer is exercised directly, the way {@code DefaultErrorHandler}
 * invokes it once retries are exhausted; the end-to-end listener path is covered by
 * {@code TimelineEventsConsumerGroupsIT}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TimelineScoringDeadLetterIT {

    private static final String TIMELINE_TOPIC = "timeline.events";
    private static final String DEAD_LETTER_TOPIC = "game.dlq";

    @Autowired
    DeadLetterPublishingRecoverer recoverer;

    @Autowired
    ConsumerFactory<Object, Object> consumerFactory;

    @Test
    void unprocessableRecord_isRoutedToDeadLetterTopic() {
        var payload = "poison-" + UUID.randomUUID();
        var failed = new ConsumerRecord<Object, Object>(
                TIMELINE_TOPIC, 0, 0L, UUID.randomUUID().toString(), payload);

        recoverer.accept(failed, new IllegalStateException("payload cannot be mapped to its event type"));

        try (var consumer = consumerFactory.createConsumer("dlq-it-" + UUID.randomUUID(), "")) {
            consumer.subscribe(List.of(DEAD_LETTER_TOPIC));
            var parked = await().atMost(Duration.ofSeconds(30))
                    .until(() -> pollForValue(consumer, payload), Objects::nonNull);

            assertThat(parked).contains(payload);
        }
    }

    private String pollForValue(Consumer<Object, Object> consumer, String expected) {
        var records = consumer.poll(Duration.ofMillis(500));
        return StreamSupport.stream(records.spliterator(), false)
                .map(ConsumerRecord::value)
                .filter(byte[].class::isInstance)
                .map(value -> new String((byte[]) value, StandardCharsets.UTF_8))
                .filter(value -> value.contains(expected))
                .findFirst()
                .orElse(null);
    }
}
