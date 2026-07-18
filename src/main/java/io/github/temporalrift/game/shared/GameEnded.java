package io.github.temporalrift.game.shared;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module event: the session module ends a game with final scores; the scoring module's query API
 * shares its {@link PlayerScoreResult} shape. Lives in {@code game.shared} - the neutral shared kernel -
 * so referencing it never creates a Spring Modulith module cycle. See {@link EventsDrawn} for the
 * rationale.
 */
public record GameEnded(UUID gameId, String endReason, List<PlayerScoreResult> finalScores) {

    public record PlayerScoreResult(UUID playerId, String faction, int score) {}
}
