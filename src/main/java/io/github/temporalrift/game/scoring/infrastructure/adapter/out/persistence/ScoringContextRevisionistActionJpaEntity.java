package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_revisionist_action")
class ScoringContextRevisionistActionJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "target_event_id", nullable = false)
    private UUID targetEventId;

    @Column(name = "target_outcome_id", nullable = false)
    private UUID targetOutcomeId;

    @Column(name = "resolved")
    private Boolean resolved;

    protected ScoringContextRevisionistActionJpaEntity() {}

    UUID getPlayerId() {
        return playerId;
    }

    String getAction() {
        return action;
    }

    UUID getTargetEventId() {
        return targetEventId;
    }

    UUID getTargetOutcomeId() {
        return targetOutcomeId;
    }

    Boolean getResolved() {
        return resolved;
    }

    void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
