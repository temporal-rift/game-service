package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ScoringEraCompletionJpaEntityTest {

    static final Instant COMPLETED_AT = Instant.parse("2026-07-14T00:00:00Z");

    @Test
    void scoringEraCompletionEntity_exposesCompositeKeyAndTimestamp() {
        var gameId = UUID.randomUUID();

        var entity = new ScoringEraCompletionJpaEntity(gameId, 2, COMPLETED_AT);

        assertThat(entity.getId().getGameId()).isEqualTo(gameId);
        assertThat(entity.getId().getEraNumber()).isEqualTo(2);
        assertThat(entity.getCompletedAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void scoringEraCompletionKey_equalityIncludesGameIdAndEraNumber() {
        var gameId = UUID.randomUUID();
        var key = new ScoringEraCompletionJpaEntity.ScoringEraCompletionKey(gameId, 1);

        assertThat(key)
                .isEqualTo(new ScoringEraCompletionJpaEntity.ScoringEraCompletionKey(gameId, 1))
                .hasSameHashCodeAs(new ScoringEraCompletionJpaEntity.ScoringEraCompletionKey(gameId, 1))
                .isNotEqualTo(new ScoringEraCompletionJpaEntity.ScoringEraCompletionKey(gameId, 2))
                .isNotEqualTo(new ScoringEraCompletionJpaEntity.ScoringEraCompletionKey(UUID.randomUUID(), 1))
                .isNotEqualTo("not-a-key");
    }
}
