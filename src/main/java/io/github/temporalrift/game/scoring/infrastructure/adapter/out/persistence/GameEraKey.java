package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Shared composite key for tables keyed purely by (game_id, era_number). */
@Embeddable
class GameEraKey implements Serializable {

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "era_number", nullable = false)
    private int eraNumber;

    protected GameEraKey() {}

    GameEraKey(UUID gameId, int eraNumber) {
        this.gameId = Objects.requireNonNull(gameId);
        this.eraNumber = eraNumber;
    }

    UUID getGameId() {
        return gameId;
    }

    int getEraNumber() {
        return eraNumber;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GameEraKey that)) {
            return false;
        }
        return eraNumber == that.eraNumber && gameId.equals(that.gameId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameId, eraNumber);
    }
}
