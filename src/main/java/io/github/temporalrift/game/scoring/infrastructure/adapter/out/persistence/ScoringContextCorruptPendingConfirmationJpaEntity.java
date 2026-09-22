package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GameEraScopedJpaEntity;

/**
 * A Corrupt inversion confirmation that arrived before its correlation row was recorded. Merged into the
 * correlation when {@code recordCorruptCorrelation} inserts it; see the 018 migration.
 */
@Entity
@Table(name = "scoring_context_corrupt_pending_confirmation")
class ScoringContextCorruptPendingConfirmationJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "corrupting_player_id", nullable = false)
    private UUID corruptingPlayerId;

    @Column(name = "target_event_id", nullable = false)
    private UUID targetEventId;

    @Column(name = "took_effect", nullable = false)
    private boolean tookEffect;

    protected ScoringContextCorruptPendingConfirmationJpaEntity() {}

    UUID getCorruptingPlayerId() {
        return corruptingPlayerId;
    }

    void setCorruptingPlayerId(UUID corruptingPlayerId) {
        this.corruptingPlayerId = corruptingPlayerId;
    }

    UUID getTargetEventId() {
        return targetEventId;
    }

    void setTargetEventId(UUID targetEventId) {
        this.targetEventId = targetEventId;
    }

    boolean isTookEffect() {
        return tookEffect;
    }

    void setTookEffect(boolean tookEffect) {
        this.tookEffect = tookEffect;
    }
}
