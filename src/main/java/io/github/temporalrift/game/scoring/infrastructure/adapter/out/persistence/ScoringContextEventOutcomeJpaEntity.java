package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_event_outcome")
class ScoringContextEventOutcomeJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "starting_outcome_count", nullable = false)
    private int startingOutcomeCount;

    @Column(name = "written_outcome_id")
    private UUID writtenOutcomeId;

    @Column(name = "written_by_player_id")
    private UUID writtenByPlayerId;

    protected ScoringContextEventOutcomeJpaEntity() {}

    UUID getEventId() {
        return eventId;
    }

    void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    int getStartingOutcomeCount() {
        return startingOutcomeCount;
    }

    void setStartingOutcomeCount(int startingOutcomeCount) {
        this.startingOutcomeCount = startingOutcomeCount;
    }

    UUID getWrittenOutcomeId() {
        return writtenOutcomeId;
    }

    void setWrittenOutcomeId(UUID writtenOutcomeId) {
        this.writtenOutcomeId = writtenOutcomeId;
    }

    UUID getWrittenByPlayerId() {
        return writtenByPlayerId;
    }

    void setWrittenByPlayerId(UUID writtenByPlayerId) {
        this.writtenByPlayerId = writtenByPlayerId;
    }
}
