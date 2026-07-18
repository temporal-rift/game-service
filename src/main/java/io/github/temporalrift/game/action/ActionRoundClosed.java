package io.github.temporalrift.game.action;

import java.util.UUID;

/**
 * Public cross-module event published by the action module. Lives in action's top-level package on
 * purpose, following the same convention as {@link StartActionRoundRequested} so other modules may
 * reference it without reaching into action's internal {@code domain.event} package.
 */
public record ActionRoundClosed(UUID gameId, int eraNumber, int roundNumber, String closedReason, int totalActions) {}
