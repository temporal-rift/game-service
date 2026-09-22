package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GameEraScopedJpaEntity;

@Entity
@Table(name = "scoring_context_action_fact")
class ScoringContextActionFactJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "faction", nullable = false)
    private String faction;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    protected ScoringContextActionFactJpaEntity() {}

    UUID getPlayerId() {
        return playerId;
    }

    String getFaction() {
        return faction;
    }

    String getReason() {
        return reason;
    }

    boolean isConsumed() {
        return consumed;
    }

    void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }
}
