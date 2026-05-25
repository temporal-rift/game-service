package io.github.temporalrift.game.action;

import java.util.List;
import java.util.UUID;

/**
 * Public cross-module event published by the session module to request that the action module opens
 * a new action round.
 *
 * <p>This lives in the action module's top-level package on purpose. Other modules may reference this
 * type without reaching into action's internal {@code application.saga} package, which preserves the
 * intended Modulith boundary.
 */
public record StartActionRoundRequested(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {}
