package io.github.temporalrift.game.session.domain.foresight;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One Prophet's stored next-era preview for an era. Holds catalog identities only; display data is
 * resolved from the static catalog at publish and recovery time.
 */
public record ForesightReveal(
        UUID gameId, int eraNumber, UUID playerId, int nextEraNumber, List<UUID> catalogEventIds, String emptyReason) {

    public ForesightReveal {
        Objects.requireNonNull(gameId, "gameId must not be null");
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(catalogEventIds, "catalogEventIds must not be null");
        if (nextEraNumber != eraNumber + 1) {
            throw new IllegalArgumentException("nextEraNumber must be eraNumber + 1");
        }
        if (catalogEventIds.isEmpty() && emptyReason == null) {
            throw new IllegalArgumentException("emptyReason must be present when no events are revealed");
        }
        catalogEventIds = List.copyOf(catalogEventIds);
    }
}
