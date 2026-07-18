package io.github.temporalrift.game.session.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.session.domain.event.GameEnded;

public interface FinalScoreQueryPort {

    List<GameEnded.PlayerScoreResult> getScores(UUID gameId);
}
