package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_events")
class ProcessedEventJpaEntity {

    @EmbeddedId
    private ProcessedEventKey id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {}

    ProcessedEventJpaEntity(UUID eventId, String consumer, Instant processedAt) {
        this.id = new ProcessedEventKey(eventId, consumer);
        this.processedAt = Objects.requireNonNull(processedAt);
    }

    ProcessedEventKey getId() {
        return id;
    }

    Instant getProcessedAt() {
        return processedAt;
    }

    @jakarta.persistence.Embeddable
    static class ProcessedEventKey implements Serializable {

        @Column(name = "event_id", nullable = false)
        private UUID eventId;

        @Column(name = "consumer", nullable = false, length = 100)
        private String consumer;

        protected ProcessedEventKey() {}

        ProcessedEventKey(UUID eventId, String consumer) {
            this.eventId = Objects.requireNonNull(eventId);
            this.consumer = Objects.requireNonNull(consumer);
        }

        UUID getEventId() {
            return eventId;
        }

        String getConsumer() {
            return consumer;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProcessedEventKey that)) {
                return false;
            }
            return eventId.equals(that.eventId) && consumer.equals(that.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumer);
        }
    }
}
