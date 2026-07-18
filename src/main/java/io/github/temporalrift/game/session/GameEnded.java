package io.github.temporalrift.game.session;

import java.util.List;
import java.util.UUID;

/**
 * Public cross-module event published by the session module. Lives in session's top-level package
 * on purpose, following the same convention as {@link io.github.temporalrift.game.action.StartActionRoundRequested}
 * so other modules may reference it without reaching into session's internal {@code domain.event} package.
 */
public record GameEnded(UUID gameId, String endReason, List<PlayerScoreResult> finalScores) {

    public record PlayerScoreResult(UUID playerId, String faction, int score) {}
}
