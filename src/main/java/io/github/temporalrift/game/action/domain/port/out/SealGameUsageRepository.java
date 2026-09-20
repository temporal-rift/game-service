package io.github.temporalrift.game.action.domain.port.out;

import java.util.UUID;

public interface SealGameUsageRepository {

    /**
     * Counts accepted Seal uses for the player across all eras of the game, derived from the durable
     * era-usage rows. Eras run sequentially and the era budget already caps each era at one Seal, so the
     * observed count plus the current era claim stays exact without a second write.
     */
    int countAcceptedSeals(UUID gameId, UUID playerId);
}
