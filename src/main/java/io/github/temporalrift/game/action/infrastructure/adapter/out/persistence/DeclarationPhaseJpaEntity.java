package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationDecision;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GameEraScopedJpaEntity;

@Entity
@Table(name = "declaration_phase")
class DeclarationPhaseJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ElementCollection
    @CollectionTable(name = "declaration_decision", joinColumns = @JoinColumn(name = "phase_id"))
    @MapKeyColumn(name = "player_id")
    @Column(name = "decision", nullable = false)
    @Enumerated(EnumType.STRING)
    private Map<UUID, DeclarationDecision> decisions = new HashMap<>();

    Map<UUID, DeclarationDecision> getDecisions() {
        return decisions;
    }

    void setDecisions(Map<UUID, DeclarationDecision> decisions) {
        this.decisions = new HashMap<>(decisions);
    }

    protected DeclarationPhaseJpaEntity() {}

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
}
