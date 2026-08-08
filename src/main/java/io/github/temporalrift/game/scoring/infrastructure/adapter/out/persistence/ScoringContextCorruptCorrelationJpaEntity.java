package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A candidate Corrupt fact only — see {@code scoring_context_corrupt_correlation}'s migration and
 * {@code EraScoreEvaluator.eraserDecisions}: this table alone does not drive any score credit.
 */
@Entity
@Table(name = "scoring_context_corrupt_correlation")
class ScoringContextCorruptCorrelationJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "corrupting_player_id", nullable = false)
    private UUID corruptingPlayerId;

    @Column(name = "target_player_id", nullable = false)
    private UUID targetPlayerId;

    @Column(name = "card_instance_id", nullable = false)
    private UUID cardInstanceId;

    @Column(name = "target_event_id", nullable = false)
    private UUID targetEventId;

    @Column(name = "source_outcome_id")
    private UUID sourceOutcomeId;

    @Column(name = "target_outcome_id", nullable = false)
    private UUID targetOutcomeId;

    @Column(name = "took_effect")
    private Boolean tookEffect;

    protected ScoringContextCorruptCorrelationJpaEntity() {}

    UUID getCorruptingPlayerId() {
        return corruptingPlayerId;
    }

    void setCorruptingPlayerId(UUID corruptingPlayerId) {
        this.corruptingPlayerId = corruptingPlayerId;
    }

    UUID getTargetPlayerId() {
        return targetPlayerId;
    }

    void setTargetPlayerId(UUID targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    UUID getCardInstanceId() {
        return cardInstanceId;
    }

    void setCardInstanceId(UUID cardInstanceId) {
        this.cardInstanceId = cardInstanceId;
    }

    UUID getTargetEventId() {
        return targetEventId;
    }

    void setTargetEventId(UUID targetEventId) {
        this.targetEventId = targetEventId;
    }

    UUID getSourceOutcomeId() {
        return sourceOutcomeId;
    }

    void setSourceOutcomeId(UUID sourceOutcomeId) {
        this.sourceOutcomeId = sourceOutcomeId;
    }

    UUID getTargetOutcomeId() {
        return targetOutcomeId;
    }

    void setTargetOutcomeId(UUID targetOutcomeId) {
        this.targetOutcomeId = targetOutcomeId;
    }

    Boolean getTookEffect() {
        return tookEffect;
    }

    void setTookEffect(Boolean tookEffect) {
        this.tookEffect = tookEffect;
    }
}
