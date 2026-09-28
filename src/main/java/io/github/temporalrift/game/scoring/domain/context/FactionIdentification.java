package io.github.temporalrift.game.scoring.domain.context;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Whether some other player can know a player's faction from public facts. The drawn faction set is public, so
 * once every player but the subject and one observer is disclosed, that observer deduces the subject's faction
 * from their own.
 */
public final class FactionIdentification {

    private FactionIdentification() {}

    public static boolean isIdentified(UUID playerId, int playerCount, Set<UUID> disclosedPlayerIds) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        if (disclosedPlayerIds.contains(playerId)) {
            return true;
        }
        var disclosedOthers = disclosedPlayerIds.stream()
                .filter(disclosed -> !disclosed.equals(playerId))
                .count();
        return disclosedOthers >= playerCount - 2L;
    }
}
