package io.github.temporalrift.game.scoring;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.events.session.GameEnded;

public interface PlayerScoreQuery {

    List<GameEnded.PlayerScoreResult> getScores(UUID gameId);
}
