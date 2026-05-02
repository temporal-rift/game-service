package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.session.GameEnded;
import io.github.temporalrift.game.scoring.PlayerScoreQuery;
import io.github.temporalrift.game.session.domain.port.out.FinalScoreQueryPort;

@Component
class FinalScoreQueryAdapter implements FinalScoreQueryPort {

    private final PlayerScoreQuery playerScoreQuery;

    FinalScoreQueryAdapter(PlayerScoreQuery playerScoreQuery) {
        this.playerScoreQuery = playerScoreQuery;
    }

    @Override
    public List<GameEnded.PlayerScoreResult> getScores(UUID gameId) {
        return playerScoreQuery.getScores(gameId);
    }
}
