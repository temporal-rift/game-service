package io.github.temporalrift.game.shared.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the session module's era saga observed every player's terminal hand selection for
 * an era and requests that the action module opens the declaration window. Lives in {@code game.shared} -
 * the neutral shared kernel - so referencing it never creates a Spring Modulith module cycle.
 */
public record HandSelectionCompleted(UUID gameId, int eraNumber, List<UUID> playerIds) {}
