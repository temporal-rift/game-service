package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_score")
class PlayerScoreJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "faction", nullable = false)
    private String faction;

    @Column(name = "total_score", nullable = false)
    private int totalScore;

    protected PlayerScoreJpaEntity() {}

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getGameId() {
        return gameId;
    }

    void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

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
