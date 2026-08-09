package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scoring_context_paradox_cascade_fact")
class ScoringContextParadoxCascadeFactJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "paradox_id", nullable = false)
    private UUID paradoxId;

    @Column(name = "affected_event_id", nullable = false)
    private UUID affectedEventId;

    @Column(name = "detonated_by_player_ids", columnDefinition = "uuid[]", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<UUID> detonatedByPlayerIds;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    protected ScoringContextParadoxCascadeFactJpaEntity() {}

    UUID getParadoxId() {
        return paradoxId;
    }

    void setParadoxId(UUID paradoxId) {
        this.paradoxId = paradoxId;
    }

    UUID getAffectedEventId() {
        return affectedEventId;
    }

    void setAffectedEventId(UUID affectedEventId) {
        this.affectedEventId = affectedEventId;
    }

    List<UUID> getDetonatedByPlayerIds() {
        return detonatedByPlayerIds;
    }

    void setDetonatedByPlayerIds(List<UUID> detonatedByPlayerIds) {
        this.detonatedByPlayerIds = detonatedByPlayerIds;
    }

    boolean isConsumed() {
        return consumed;
    }

    void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }
}
