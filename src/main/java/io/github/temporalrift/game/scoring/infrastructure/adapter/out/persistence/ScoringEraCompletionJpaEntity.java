package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_era_completion")
class ScoringEraCompletionJpaEntity {

    @EmbeddedId
    private ScoringEraCompletionKey id;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected ScoringEraCompletionJpaEntity() {}

    ScoringEraCompletionJpaEntity(UUID gameId, int eraNumber, Instant completedAt) {
        this.id = new ScoringEraCompletionKey(gameId, eraNumber);
        this.completedAt = Objects.requireNonNull(completedAt);
    }

    ScoringEraCompletionKey getId() {
        return id;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    @Embeddable
    static class ScoringEraCompletionKey implements Serializable {

        @Column(name = "game_id", nullable = false)
        private UUID gameId;

        @Column(name = "era_number", nullable = false)
        private int eraNumber;

        protected ScoringEraCompletionKey() {}

        ScoringEraCompletionKey(UUID gameId, int eraNumber) {
            this.gameId = Objects.requireNonNull(gameId);
            this.eraNumber = eraNumber;
        }

        UUID getGameId() {
            return gameId;
        }

        int getEraNumber() {
            return eraNumber;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ScoringEraCompletionKey that)) {
                return false;
            }
            return eraNumber == that.eraNumber && gameId.equals(that.gameId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gameId, eraNumber);
        }
    }
}
