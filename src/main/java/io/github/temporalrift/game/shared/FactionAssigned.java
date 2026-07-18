package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Cross-module event: the session module assigns a faction to a player; the action and scoring modules
 * project from it. Lives in {@code game.shared} - the neutral shared kernel - so referencing it never
 * creates a Spring Modulith module cycle. See {@link EventsDrawn} for the rationale.
 */
public record FactionAssigned(UUID gameId, UUID playerId, String faction) {}
