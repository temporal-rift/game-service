package io.github.temporalrift.game.scoring.domain.event;

import java.util.UUID;

/** A Weaver's pending chain link cleared without confirming; {@code cause} says whether that was a miss. */
public record ChainLinkInvalidated(UUID gameId, int eraNumber, UUID chainId, UUID playerId, Cause cause) {

    public enum Cause {
        RESOLVED_DIFFERENTLY,
        NO_WINNER,
        CHAIN_CONFLICT_CLEARED,
        ANNIHILATE_ABSORBED,
        GAME_ENDED
    }

    /** Only a link whose event resolved to another winner is a miss; every other clear is not the Weaver's fault. */
    public boolean isMiss() {
        return cause == Cause.RESOLVED_DIFFERENTLY;
    }
}
