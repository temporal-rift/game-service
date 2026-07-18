package io.github.temporalrift.game.session.domain.event;

import java.util.List;
import java.util.UUID;

public record GameEnded(UUID gameId, String endReason, List<PlayerScoreResult> finalScores) {

    public record PlayerScoreResult(UUID playerId, String faction, int score) {}
}
