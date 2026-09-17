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

@Service
@Transactional(readOnly = true)
public class FactionObjectiveQueryService implements FactionObjectiveQuery {

    private final PlayerScoreRepository playerScoreRepository;
    private final VictoryRulesPort victoryRulesPort;

    public FactionObjectiveQueryService(
            PlayerScoreRepository playerScoreRepository, VictoryRulesPort victoryRulesPort) {
        this.playerScoreRepository = playerScoreRepository;
        this.victoryRulesPort = victoryRulesPort;
    }

    @Override
    public List<ObjectiveProgress> evaluate(UUID gameId, int eraNumber) {
        Objects.requireNonNull(gameId, "gameId must not be null");
        return playerScoreRepository.findAllByGameId(gameId).stream()
                .map(score -> {
                    var progress = FactionObjectiveEvaluator.evaluate(score, eraNumber, victoryRulesPort);
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
