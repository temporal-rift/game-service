package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_context_annihilated_outcome")
class ScoringContextAnnihilatedOutcomeJpaEntity extends GameEraScopedJpaEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "outcome_id", nullable = false)
    private UUID outcomeId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    protected ScoringContextAnnihilatedOutcomeJpaEntity() {}

    UUID getEventId() {
        return eventId;
    }

    void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    UUID getOutcomeId() {
        return outcomeId;
    }

    void setOutcomeId(UUID outcomeId) {
        this.outcomeId = outcomeId;
    }

    UUID getPlayerId() {
        return playerId;
    }

    void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }
}
