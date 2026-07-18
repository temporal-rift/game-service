package io.github.temporalrift.game.scoring.application.query;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.PlayerScoreQuery;
import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.session.GameEnded;

@Service
@Transactional(readOnly = true)
public class PlayerScoreQueryService implements PlayerScoreQuery {

    private final PlayerScoreRepository playerScoreRepository;

    public PlayerScoreQueryService(PlayerScoreRepository playerScoreRepository) {
        this.playerScoreRepository = playerScoreRepository;
    }

    @Override
    public List<GameEnded.PlayerScoreResult> getScores(UUID gameId) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        return playerScoreRepository.findAllByGameId(gameId).stream()
                .sorted(Comparator.comparing(PlayerScore::playerId))
                .map(score -> new GameEnded.PlayerScoreResult(
                        score.playerId(), score.faction().name(), score.totalScore()))
                .toList();
    }
}
