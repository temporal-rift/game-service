package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.events.timeline.OutcomeApplied;

@Entity
@Table(name = "scoring_timeline_outcome_inbox")
class ScoringTimelineOutcomeInboxJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "winning_outcome_id", nullable = false)
    private UUID winningOutcomeId;

    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private OutcomeApplied payload;

    protected ScoringTimelineOutcomeInboxJpaEntity() {}

    static ScoringTimelineOutcomeInboxJpaEntity fromDomain(OutcomeApplied outcome) {
        var entity = new ScoringTimelineOutcomeInboxJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setGameId(outcome.gameId());
        entity.setEraNumber(outcome.eraNumber());
        entity.setEventId(outcome.eventId());
        entity.setWinningOutcomeId(outcome.winningOutcomeId());
        entity.setPayload(outcome);
        return entity;
    }

    OutcomeApplied toDomain() {
        return payload;
    }

    UUID getEventId() {
        return eventId;
    }

    void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    UUID getWinningOutcomeId() {
        return winningOutcomeId;
    }

    void setWinningOutcomeId(UUID winningOutcomeId) {
        this.winningOutcomeId = winningOutcomeId;
    }

    OutcomeApplied getPayload() {
        return payload;
    }

    void setPayload(OutcomeApplied payload) {
        this.payload = payload;
    }
}
