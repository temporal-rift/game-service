package io.github.temporalrift.game.shared;

import java.util.UUID;

/** Cross-module scoring fact for a qualifying Expose response, with no private response target. */
public record ExposeBehaviorChanged(UUID gameId, int eraNumber, UUID activistPlayerId, UUID targetPlayerId) {}
