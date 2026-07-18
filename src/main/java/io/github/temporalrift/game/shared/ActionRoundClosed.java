package io.github.temporalrift.game.shared;

import java.util.UUID;

/**
 * Cross-module event: the action module closes an action round; the session module's era-advancement
 * saga reacts to it. Lives in {@code game.shared} - the neutral shared kernel - so referencing it never
 * creates a Spring Modulith module cycle. See {@link EventsDrawn} for the rationale.
 */
public record ActionRoundClosed(UUID gameId, int eraNumber, int roundNumber, String closedReason, int totalActions) {}
