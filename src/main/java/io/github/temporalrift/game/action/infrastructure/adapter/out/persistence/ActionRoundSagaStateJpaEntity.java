package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "action_round_saga_state")
class ActionRoundSagaStateJpaEntity {

    @Id
    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "pending_player_ids", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = UuidListConverter.class)
    private List<UUID> pendingPlayerIds;

    @Column(name = "timer_expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant timerExpiresAt;

    protected ActionRoundSagaStateJpaEntity() {}

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

    public int getEraNumber() {
        return eraNumber;
    }

    public void setEraNumber(int eraNumber) {
        this.eraNumber = eraNumber;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<UUID> getPendingPlayerIds() {
        return pendingPlayerIds;
    }

    public void setPendingPlayerIds(List<UUID> pendingPlayerIds) {
        this.pendingPlayerIds = pendingPlayerIds;
    }

    public Instant getTimerExpiresAt() {
        return timerExpiresAt;
    }

    public void setTimerExpiresAt(Instant timerExpiresAt) {
        this.timerExpiresAt = timerExpiresAt;
    }
}
