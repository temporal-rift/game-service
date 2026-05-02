package io.github.temporalrift.game.scoring.application.query;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.events.session.GameEnded;
import io.github.temporalrift.game.scoring.PlayerScoreQuery;

@Service
@Transactional(readOnly = true)
public class PlayerScoreQueryService implements PlayerScoreQuery {

    @Override
    public List<GameEnded.PlayerScoreResult> getScores(UUID gameId) {
        // TODO: implement when scoring module persistence is added
        return List.of();
    }
}
