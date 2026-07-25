package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_player")
class ScoringPlayerJpaEntity extends GamePlayerScopedJpaEntity {

    @Column(name = "player_name", nullable = false)
    private String playerName;

    protected ScoringPlayerJpaEntity() {}

    String getPlayerName() {
        return playerName;
    }

    void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}
