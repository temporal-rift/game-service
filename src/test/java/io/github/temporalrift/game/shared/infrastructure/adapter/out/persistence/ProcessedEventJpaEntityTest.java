package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProcessedEventJpaEntityTest {

    @Test
    void processedEventEntity_exposesCompositeKeyAndTimestamp() {
        var eventId = UUID.randomUUID();
        var processedAt = Instant.now();

        var entity = new ProcessedEventJpaEntity(eventId, "session.player-reconnect", processedAt);

        assertThat(entity.getId().getEventId()).isEqualTo(eventId);
        assertThat(entity.getId().getConsumer()).isEqualTo("session.player-reconnect");
        assertThat(entity.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    void processedEventKey_equalityIncludesEventIdAndConsumer() {
        var eventId = UUID.randomUUID();
        var key = new ProcessedEventJpaEntity.ProcessedEventKey(eventId, "session.player-reconnect");

        assertThat(key)
                .isEqualTo(new ProcessedEventJpaEntity.ProcessedEventKey(eventId, "session.player-reconnect"))
                .hasSameHashCodeAs(new ProcessedEventJpaEntity.ProcessedEventKey(eventId, "session.player-reconnect"))
                .isNotEqualTo(new ProcessedEventJpaEntity.ProcessedEventKey(eventId, "session.paradox-cascaded"))
                .isNotEqualTo(
                        new ProcessedEventJpaEntity.ProcessedEventKey(UUID.randomUUID(), "session.player-reconnect"))
                .isNotEqualTo("session.player-reconnect");
    }
}
