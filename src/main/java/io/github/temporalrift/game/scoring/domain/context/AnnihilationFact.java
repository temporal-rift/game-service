package io.github.temporalrift.game.scoring.domain.context;

import java.util.UUID;

/** One Eraser's successful annihilation of one outcome, attributed to the player who performed it. */
public record AnnihilationFact(UUID eventId, UUID outcomeId, UUID playerId) {}
