package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;

@Entity
@Table(name = "action_round")
class ActionRoundJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "timer_seconds", nullable = false)
    private int timerSeconds;

    @Column(name = "closed_reason")
    private String closedReason;

    @Column(name = "pending_player_ids", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = UuidListConverter.class)
    private List<UUID> pendingPlayerIds;

    @Column(name = "submitted_actions", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = SubmittedActionListConverter.class)
    private List<SubmittedAction> submittedActions;

    protected ActionRoundJpaEntity() {}

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

    int getEraNumber() {
        return eraNumber;
    }

    void setEraNumber(int eraNumber) {
        this.eraNumber = eraNumber;
    }

    int getRoundNumber() {
        return roundNumber;
    }

    void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    int getTimerSeconds() {
        return timerSeconds;
    }

    void setTimerSeconds(int timerSeconds) {
        this.timerSeconds = timerSeconds;
    }

    String getClosedReason() {
        return closedReason;
    }

    void setClosedReason(String closedReason) {
        this.closedReason = closedReason;
    }

    List<UUID> getPendingPlayerIds() {
        return pendingPlayerIds;
    }

    void setPendingPlayerIds(List<UUID> pendingPlayerIds) {
        this.pendingPlayerIds = pendingPlayerIds;
    }

    List<SubmittedAction> getSubmittedActions() {
        return submittedActions;
    }

    void setSubmittedActions(List<SubmittedAction> submittedActions) {
        this.submittedActions = submittedActions;
    }
}
