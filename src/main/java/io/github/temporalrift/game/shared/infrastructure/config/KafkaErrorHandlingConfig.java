package io.github.temporalrift.game.shared.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer error handling. Without this, a poison message (for example a payload that cannot be
 * mapped to its event type) would be retried by the default handler and then dropped with only a log
 * line once retries are exhausted. Instead we retry a few times for transient faults and then park the
 * record on {@code game.dlq} so it can be investigated and replayed.
 */
@Configuration
class KafkaErrorHandlingConfig {

    static final String DEAD_LETTER_TOPIC = "game.dlq";
    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long MAX_RETRIES = 2L;

    @Bean
    NewTopic gameDeadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaOperations<Object, Object> kafkaOperations) {
        // Partition -1 lets the broker choose the DLQ partition, so game.dlq does not need the same
        // partition count as the source topics.
        return new DeadLetterPublishingRecoverer(
                kafkaOperations, (consumerRecord, exception) -> new TopicPartition(DEAD_LETTER_TOPIC, -1));
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    }
}
