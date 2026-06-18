package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProcessedEventJpaEntityTest {

    static final Instant PROCESSED_AT = Instant.parse("2026-06-18T00:00:00Z");

    @Test
    void processedEventEntity_exposesCompositeKeyAndTimestamp() {
        var eventId = UUID.randomUUID();

        var entity = new ProcessedEventJpaEntity(eventId, "session.player-reconnect", PROCESSED_AT);

        assertThat(entity.getId().getEventId()).isEqualTo(eventId);
        assertThat(entity.getId().getConsumer()).isEqualTo("session.player-reconnect");
        assertThat(entity.getProcessedAt()).isEqualTo(PROCESSED_AT);
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
