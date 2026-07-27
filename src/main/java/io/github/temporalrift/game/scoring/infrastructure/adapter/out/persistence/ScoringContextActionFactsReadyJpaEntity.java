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
@Table(name = "scoring_context_action_facts_ready")
class ScoringContextActionFactsReadyJpaEntity {

    @EmbeddedId
    private ScoringContextActionFactsReadyKey id;

    @Column(name = "ready_at", nullable = false)
    private Instant readyAt;

    protected ScoringContextActionFactsReadyJpaEntity() {}

    ScoringContextActionFactsReadyJpaEntity(UUID gameId, int eraNumber, Instant readyAt) {
        this.id = new ScoringContextActionFactsReadyKey(gameId, eraNumber);
        this.readyAt = Objects.requireNonNull(readyAt);
    }

    ScoringContextActionFactsReadyKey getId() {
        return id;
    }

    Instant getReadyAt() {
        return readyAt;
    }

    @Embeddable
    static class ScoringContextActionFactsReadyKey implements Serializable {

        @Column(name = "game_id", nullable = false)
        private UUID gameId;

        @Column(name = "era_number", nullable = false)
        private int eraNumber;

        protected ScoringContextActionFactsReadyKey() {}

        ScoringContextActionFactsReadyKey(UUID gameId, int eraNumber) {
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
            if (!(other instanceof ScoringContextActionFactsReadyKey that)) {
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
