package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GameEraScopedJpaEntity;

@Entity
@Table(name = "paradox_resolution_phase")
class ParadoxResolutionPhaseJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "affected_event_ids", columnDefinition = "uuid[]", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private UUID[] affectedEventIds;

    @Column(name = "submitted_player_ids", columnDefinition = "uuid[]", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private UUID[] submittedPlayerIds;

    protected ParadoxResolutionPhaseJpaEntity() {}

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    UUID[] getAffectedEventIds() {
        return affectedEventIds;
    }

    void setAffectedEventIds(UUID[] affectedEventIds) {
        this.affectedEventIds = affectedEventIds;
    }

    UUID[] getSubmittedPlayerIds() {
        return submittedPlayerIds;
    }

    void setSubmittedPlayerIds(UUID[] submittedPlayerIds) {
        this.submittedPlayerIds = submittedPlayerIds;
    }
}
