package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.github.temporalrift.events.timeline.OutcomeApplied;

@Entity
@Table(name = "scoring_timeline_outcome_inbox")
class ScoringTimelineOutcomeInboxJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

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
