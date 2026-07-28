package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_era_completion")
class ScoringEraCompletionJpaEntity {

    @EmbeddedId
    private GameEraKey id;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected ScoringEraCompletionJpaEntity() {}

    ScoringEraCompletionJpaEntity(UUID gameId, int eraNumber, Instant completedAt) {
        this.id = new GameEraKey(gameId, eraNumber);
        this.completedAt = Objects.requireNonNull(completedAt);
    }

    GameEraKey getId() {
        return id;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
