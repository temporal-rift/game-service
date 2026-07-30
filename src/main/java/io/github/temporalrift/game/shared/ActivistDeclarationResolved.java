package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Cross-module outcome of scoring's durable Activist declaration correlation.
 *
 * <p>The action module consumes this event to carry Momentum eligibility into the next era without reading
 * scoring-owned projections.
 */
public record ActivistDeclarationResolved(UUID gameId, int eraNumber, UUID playerId, boolean succeeded) {}
