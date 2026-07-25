package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scoring_game_visibility")
class ScoringGameVisibilityJpaEntity {

    @Id
    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "factions_revealed", nullable = false)
    private boolean factionsRevealed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScoringGameVisibilityJpaEntity() {}

    UUID getGameId() {
        return gameId;
    }

    void setGameId(UUID gameId) {
        this.gameId = gameId;
    }

    boolean isFactionsRevealed() {
        return factionsRevealed;
    }

    void setFactionsRevealed(boolean factionsRevealed) {
        this.factionsRevealed = factionsRevealed;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
