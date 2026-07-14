package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreEntry;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

@Entity
@Table(name = "player_score_history")
class PlayerScoreHistoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "player_score_id", nullable = false)
    private UUID playerScoreId;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "points_delta", nullable = false)
    private int pointsDelta;

    @Column(name = "new_total", nullable = false)
    private int newTotal;

    protected PlayerScoreHistoryJpaEntity() {}

    static PlayerScoreHistoryJpaEntity fromDomain(UUID playerScoreId, UUID gameId, UUID playerId, ScoreEntry entry) {
        var jpaEntity = new PlayerScoreHistoryJpaEntity();
        jpaEntity.setId(UUID.randomUUID());
        jpaEntity.setPlayerScoreId(playerScoreId);
        jpaEntity.setGameId(gameId);
        jpaEntity.setPlayerId(playerId);
        jpaEntity.setEraNumber(entry.eraNumber());
        jpaEntity.setReason(entry.reason().name());
        jpaEntity.setPointsDelta(entry.pointsDelta());
        jpaEntity.setNewTotal(entry.newTotal());
        return jpaEntity;
    }

    ScoreEntry toDomain() {
        return new ScoreEntry(eraNumber, ScoreReason.valueOf(reason), pointsDelta, newTotal);
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getPlayerScoreId() {
        return playerScoreId;
    }

    void setPlayerScoreId(UUID playerScoreId) {
        this.playerScoreId = playerScoreId;
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

    int getEraNumber() {
        return eraNumber;
    }

    void setEraNumber(int eraNumber) {
        this.eraNumber = eraNumber;
    }

    String getReason() {
        return reason;
    }

    void setReason(String reason) {
        this.reason = reason;
    }

    int getPointsDelta() {
        return pointsDelta;
    }

    void setPointsDelta(int pointsDelta) {
        this.pointsDelta = pointsDelta;
    }

    int getNewTotal() {
        return newTotal;
    }

    void setNewTotal(int newTotal) {
        this.newTotal = newTotal;
    }
}
