package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_action_facts_ready")
class ScoringContextActionFactsReadyJpaEntity {

    @EmbeddedId
    private GameEraKey id;

    @Column(name = "ready_at", nullable = false)
    private Instant readyAt;

    protected ScoringContextActionFactsReadyJpaEntity() {}

    ScoringContextActionFactsReadyJpaEntity(UUID gameId, int eraNumber, Instant readyAt) {
        this.id = new GameEraKey(gameId, eraNumber);
        this.readyAt = Objects.requireNonNull(readyAt);
    }

    GameEraKey getId() {
        return id;
    }

    Instant getReadyAt() {
        return readyAt;
    }
}
