package io.github.temporalrift.game.session.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.foresight.ForesightReveal;

public interface ForesightRevealRepository {

    /**
     * Stores the reveal unless one already exists for the same game, era, and viewer. Returns
     * whether this call stored a new reveal.
     */
    boolean saveIfAbsent(ForesightReveal reveal);

    Optional<ForesightReveal> findByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId);
}
