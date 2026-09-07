package io.github.temporalrift.game.action.domain.specialactionerausage;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.shared.AggregateRoot;
import io.github.temporalrift.game.shared.SpecialAction;

/**
 * The era-scoped record of which once-per-era-budgeted specials a player has already used, independently of any
 * individual action-round aggregate.
 */
public final class SpecialActionEraUsage extends AggregateRoot {

    public static final String AGGREGATE_TYPE = "SpecialActionEraUsage";

    private final UUID id;
    private final UUID gameId;
    private final int eraNumber;
    private final UUID playerId;
    private final Set<SpecialAction> claimedSpecials;

    public SpecialActionEraUsage(UUID id, UUID gameId, int eraNumber, UUID playerId) {
        this(id, gameId, eraNumber, playerId, EnumSet.noneOf(SpecialAction.class));
    }

    private SpecialActionEraUsage(
            UUID id, UUID gameId, int eraNumber, UUID playerId, Set<SpecialAction> claimedSpecials) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.gameId = Objects.requireNonNull(gameId, "gameId must not be null");
        this.eraNumber = eraNumber;
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.claimedSpecials = new HashSet<>(claimedSpecials);
    }

    public static SpecialActionEraUsage reconstitute(
            UUID id, UUID gameId, int eraNumber, UUID playerId, Set<SpecialAction> claimedSpecials) {
        return new SpecialActionEraUsage(id, gameId, eraNumber, playerId, claimedSpecials);
    }

    /**
     * Records this era's use of {@code specialAction} for this player. Throws if that same special was already
     * claimed this era; leaves the claim set unchanged when it does.
     */
    public void claim(SpecialAction specialAction) {
        Objects.requireNonNull(specialAction, "specialAction must not be null");
        if (!claimedSpecials.add(specialAction)) {
            throw new SpecialActionEraBudgetExhaustedException(playerId, specialAction, eraNumber);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID gameId() {
        return gameId;
    }

    public int eraNumber() {
        return eraNumber;
    }

    public UUID playerId() {
        return playerId;
    }

    public Set<SpecialAction> claimedSpecials() {
        return Set.copyOf(claimedSpecials);
    }
}
