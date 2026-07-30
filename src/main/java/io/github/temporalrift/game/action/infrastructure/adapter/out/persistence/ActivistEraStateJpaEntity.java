package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "activist_era_state")
class ActivistEraStateJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "activist_player_id", nullable = false)
    private UUID activistPlayerId;

    @Column(name = "momentum_eligible", nullable = false)
    private boolean momentumEligible;

    @Column(name = "declaration_succeeded", nullable = false)
    private boolean declarationSucceeded;

    @Column(name = "declaration_mode")
    private String declarationMode;

    @Column(name = "target_event_id")
    private UUID targetEventId;

    @Column(name = "target_outcome_id")
    private UUID targetOutcomeId;

    @Column(name = "exposed_player_id")
    private UUID exposedPlayerId;

    @Column(name = "exposed_signature_type")
    private String exposedSignatureType;

    @Column(name = "exposed_signature_event_id")
    private UUID exposedSignatureEventId;

    @Column(name = "exposed_signature_source_outcome_id")
    private UUID exposedSignatureSourceOutcomeId;

    @Column(name = "exposed_signature_target_outcome_id")
    private UUID exposedSignatureTargetOutcomeId;

    @Column(name = "expose_behavior_changed", nullable = false)
    private boolean exposeBehaviorChanged;

    protected ActivistEraStateJpaEntity() {}

    UUID getActivistPlayerId() {
        return activistPlayerId;
    }

    void setActivistPlayerId(UUID activistPlayerId) {
        this.activistPlayerId = activistPlayerId;
    }

    boolean isMomentumEligible() {
        return momentumEligible;
    }

    void setMomentumEligible(boolean momentumEligible) {
        this.momentumEligible = momentumEligible;
    }

    boolean isDeclarationSucceeded() {
        return declarationSucceeded;
    }

    void setDeclarationSucceeded(boolean declarationSucceeded) {
        this.declarationSucceeded = declarationSucceeded;
    }

    String getDeclarationMode() {
        return declarationMode;
    }

    void setDeclarationMode(String declarationMode) {
        this.declarationMode = declarationMode;
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

    UUID getExposedPlayerId() {
        return exposedPlayerId;
    }

    void setExposedPlayerId(UUID exposedPlayerId) {
        this.exposedPlayerId = exposedPlayerId;
    }

    String getExposedSignatureType() {
        return exposedSignatureType;
    }

    void setExposedSignatureType(String exposedSignatureType) {
        this.exposedSignatureType = exposedSignatureType;
    }

    UUID getExposedSignatureEventId() {
        return exposedSignatureEventId;
    }

    void setExposedSignatureEventId(UUID exposedSignatureEventId) {
        this.exposedSignatureEventId = exposedSignatureEventId;
    }

    UUID getExposedSignatureSourceOutcomeId() {
        return exposedSignatureSourceOutcomeId;
    }

    void setExposedSignatureSourceOutcomeId(UUID exposedSignatureSourceOutcomeId) {
        this.exposedSignatureSourceOutcomeId = exposedSignatureSourceOutcomeId;
    }

    UUID getExposedSignatureTargetOutcomeId() {
        return exposedSignatureTargetOutcomeId;
    }

    void setExposedSignatureTargetOutcomeId(UUID exposedSignatureTargetOutcomeId) {
        this.exposedSignatureTargetOutcomeId = exposedSignatureTargetOutcomeId;
    }

    boolean isExposeBehaviorChanged() {
        return exposeBehaviorChanged;
    }

    void setExposeBehaviorChanged(boolean exposeBehaviorChanged) {
        this.exposeBehaviorChanged = exposeBehaviorChanged;
    }
}
