package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "seal_game_usage")
class SealGameUsageJpaEntity extends GamePlayerScopedJpaEntity {

    @Column(name = "accepted_uses", nullable = false)
    private int acceptedUses;

    protected SealGameUsageJpaEntity() {}

    int getAcceptedUses() {
        return acceptedUses;
    }

    void setAcceptedUses(int acceptedUses) {
        this.acceptedUses = acceptedUses;
    }
}
