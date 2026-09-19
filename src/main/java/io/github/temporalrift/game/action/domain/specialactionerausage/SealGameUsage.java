package io.github.temporalrift.game.action.domain.specialactionerausage;

import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.AggregateRoot;

/**
 * The game-scoped record of how many accepted Seal uses a player has spent. The per-era budget stays in
 * {@link SpecialActionEraUsage}; this aggregate enforces the additional game-wide maximum.
 */
public final class SealGameUsage extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "SealGameUsage";

    private final UUID id;
    private final UUID gameId;
    private final UUID playerId;
    private int acceptedUses;

    public SealGameUsage(UUID id, UUID gameId, UUID playerId) {
        this(id, gameId, playerId, 0);
    }

    private SealGameUsage(UUID id, UUID gameId, UUID playerId, int acceptedUses) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        if (acceptedUses < 0) {
            throw new IllegalArgumentException("acceptedUses must not be negative");
        }
        this.acceptedUses = acceptedUses;
    }

    public static SealGameUsage reconstitute(UUID id, UUID gameId, UUID playerId, int acceptedUses) {
        return new SealGameUsage(id, gameId, playerId, acceptedUses);
    }

    /**
     * Records one accepted Seal use. Throws without changing state when the game-wide maximum is reached.
     */
    public void claim(int maxUsesPerGame) {
        if (maxUsesPerGame < 1) {
            throw new IllegalArgumentException("maxUsesPerGame must be positive");
        }
        if (acceptedUses >= maxUsesPerGame) {
            throw new SealGameBudgetExhaustedException(playerId, maxUsesPerGame);
        }
        acceptedUses++;
    }

    public int remainingUses(int maxUsesPerGame) {
        return Math.max(0, maxUsesPerGame - acceptedUses);
    }

    public UUID id() {
        return id;
    }

    public UUID gameId() {
        return gameId;
    }

    public UUID playerId() {
        return playerId;
    }

    public int acceptedUses() {
        return acceptedUses;
    }
}
