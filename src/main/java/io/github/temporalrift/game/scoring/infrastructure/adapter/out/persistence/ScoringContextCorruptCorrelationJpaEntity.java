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
}
