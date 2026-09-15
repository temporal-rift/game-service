package io.github.temporalrift.game.scoring;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.event.GameEnded;

public interface PlayerScoreQuery {

    List<GameEnded.PlayerScoreResult> getScores(UUID gameId);
}
