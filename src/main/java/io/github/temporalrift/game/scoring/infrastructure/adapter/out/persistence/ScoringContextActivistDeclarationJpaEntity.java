package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_activist_declaration")
class ScoringContextActivistDeclarationJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "mode", nullable = false)
    private String mode;

    @Column(name = "target_event_id", nullable = false)
    private UUID targetEventId;

    @Column(name = "target_outcome_id", nullable = false)
    private UUID targetOutcomeId;

    @Column(name = "resolution_succeeded")
    private Boolean resolutionSucceeded;

    protected ScoringContextActivistDeclarationJpaEntity() {}

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    String getMode() {
        return mode;
    }

    void setMode(String mode) {
        this.mode = mode;
    }

    UUID getTargetEventId() {
        return targetEventId;
    }

    void setTargetEventId(UUID targetEventId) {
        this.targetEventId = targetEventId;
    }

    UUID getTargetOutcomeId() {
        return targetOutcomeId;
    }

    void setTargetOutcomeId(UUID targetOutcomeId) {
        this.targetOutcomeId = targetOutcomeId;
    }

    Boolean getResolutionSucceeded() {
        return resolutionSucceeded;
    }

    void setResolutionSucceeded(Boolean resolutionSucceeded) {
        this.resolutionSucceeded = resolutionSucceeded;
    }
}
