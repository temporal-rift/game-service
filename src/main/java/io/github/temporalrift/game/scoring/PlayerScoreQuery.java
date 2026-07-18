package io.github.temporalrift.game.scoring;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.event.GameEnded;

public interface PlayerScoreQuery {

    List<GameEnded.PlayerScoreResult> getScores(UUID gameId);
}
