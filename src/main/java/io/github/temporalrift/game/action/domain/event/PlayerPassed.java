package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

/**
 * In-process fact that a player passed an action round, consuming their slot without acting. Deliberately
 * not an {@link ActionEventPayload}: a pass is never published on its own, and appears publicly only at
 * round close as the same neutral skip a timer expiry produces.
 */
public record PlayerPassed(UUID gameId, int eraNumber, int roundNumber, UUID playerId) {}
