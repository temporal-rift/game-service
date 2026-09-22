package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GamePlayerScopedJpaEntity;

@Entity
@Table(name = "scoring_context_player")
class ScoringContextPlayerJpaEntity extends GamePlayerScopedJpaEntity {

    @Column(name = "faction", nullable = false)
    private String faction;

    protected ScoringContextPlayerJpaEntity() {}

    String getFaction() {
        return faction;
    }

    void setFaction(String faction) {
        this.faction = faction;
    }
}
