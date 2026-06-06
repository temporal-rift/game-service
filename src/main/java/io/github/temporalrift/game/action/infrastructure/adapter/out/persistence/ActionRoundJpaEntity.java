package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;

@Entity
@Table(name = "action_round")
class ActionRoundJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "timer_seconds", nullable = false)
    private int timerSeconds;

    @Column(name = "closed_reason")
    private String closedReason;

    @Column(name = "pending_player_ids", columnDefinition = "uuid[]", nullable = false)
    @JdbcTypeCode(SqlTypes.ARRAY)
    private UUID[] pendingPlayerIds;

    @Column(name = "submitted_actions", columnDefinition = "jsonb", nullable = false)
    @Convert(converter = SubmittedActionListConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<SubmittedAction> submittedActions;

    protected ActionRoundJpaEntity() {}

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

    UUID[] getPendingPlayerIds() {
        return pendingPlayerIds;
    }

    void setPendingPlayerIds(UUID[] pendingPlayerIds) {
        this.pendingPlayerIds = pendingPlayerIds;
    }

    List<SubmittedAction> getSubmittedActions() {
        return submittedActions;
    }

    void setSubmittedActions(List<SubmittedAction> submittedActions) {
        this.submittedActions = submittedActions;
    }
}
