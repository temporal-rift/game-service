package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_reconnect_saga_state")
public class PlayerReconnectSagaStateJpaEntity {

    @Id
    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "grace_expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant graceExpiresAt;

    protected PlayerReconnectSagaStateJpaEntity() {}

    public UUID getSagaId() {
        return sagaId;
    }

    public void setSagaId(UUID sagaId) {
        this.sagaId = sagaId;
    }

    public UUID getGameId() {
        return gameId;
    }

    public void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getGraceExpiresAt() {
        return graceExpiresAt;
    }

    public void setGraceExpiresAt(Instant graceExpiresAt) {
        this.graceExpiresAt = graceExpiresAt;
    }
}
