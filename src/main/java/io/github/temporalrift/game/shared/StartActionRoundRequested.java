package io.github.temporalrift.game.shared;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the session module's era-advancement saga requests that the action module opens a
 * new action round. Lives in {@code game.shared} - the neutral shared kernel - so referencing it never
 * creates a Spring Modulith module cycle. See {@link EventsDrawn} for the rationale.
 */
public record StartActionRoundRequested(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds) {}
