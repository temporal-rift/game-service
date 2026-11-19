package io.github.temporalrift.game.scoring.application.query;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.FactionObjectiveQuery;
import io.github.temporalrift.game.scoring.domain.port.out.PlayerScoreRepository;
import io.github.temporalrift.game.scoring.domain.port.out.VictoryRulesPort;
import io.github.temporalrift.game.scoring.domain.victory.FactionObjectiveEvaluator;
import io.github.temporalrift.game.shared.domain.port.out.GameRulesPort;

@Service
@Transactional(readOnly = true)
public class FactionObjectiveQueryService implements FactionObjectiveQuery {

    private final PlayerScoreRepository playerScoreRepository;
    private final VictoryRulesPort victoryRulesPort;
    private final GameRulesPort gameRules;

    public FactionObjectiveQueryService(
            PlayerScoreRepository playerScoreRepository, VictoryRulesPort victoryRulesPort, GameRulesPort gameRules) {
        this.playerScoreRepository = playerScoreRepository;
        this.victoryRulesPort = victoryRulesPort;
        this.gameRules = gameRules;
    }

    @Override
    public List<ObjectiveProgress> evaluate(UUID gameId, int eraNumber) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        boolean finalEra = eraNumber == gameRules.maxEras();
        var scores = playerScoreRepository.findAllByGameId(gameId);
        int playerCount = scores.size();
        return scores.stream()
                .map(score -> {
                    var progress = FactionObjectiveEvaluator.evaluate(
                            score, eraNumber, finalEra, playerCount, victoryRulesPort);
                    return new ObjectiveProgress(
                            score.playerId(),
                            score.faction(),
                            progress.progressCount(),
                            progress.threshold(),
                            progress.objectiveMet());
                })
                .toList();
    }
}
