package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GameEraScopedJpaEntity;

/** One player's Stabilize on one event in an era's paradox resolution. */
@Entity
@Table(name = "scoring_context_stabilize")
class ScoringContextStabilizeJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "target_event_id", nullable = false)
    private UUID targetEventId;

    protected ScoringContextStabilizeJpaEntity() {}

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    UUID getTargetEventId() {
        return targetEventId;
    }

    void setTargetEventId(UUID targetEventId) {
        this.targetEventId = targetEventId;
    }
}
