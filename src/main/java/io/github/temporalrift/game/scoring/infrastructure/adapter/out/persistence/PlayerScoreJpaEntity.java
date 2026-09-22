package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence.GamePlayerScopedJpaEntity;

@Entity
@Table(name = "player_score")
class PlayerScoreJpaEntity extends GamePlayerScopedJpaEntity {

    @Column(name = "faction", nullable = false)
    private String faction;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    protected PlayerScoreJpaEntity() {}

    String getFaction() {
        return faction;
    }

    void setFaction(String faction) {
        this.faction = faction;
    }

    int getTotalScore() {
        return totalScore;
    }

    void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }
}
