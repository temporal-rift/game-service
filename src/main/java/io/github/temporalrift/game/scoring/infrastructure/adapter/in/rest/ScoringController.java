package io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.github.temporalrift.game.scoring.application.port.in.GetScoresUseCase;
import io.github.temporalrift.game.scoring.application.port.in.GetScoringHistoryUseCase;
import io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest.v1.ScoringApi;
import io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest.v1.model.EraHistory;
import io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest.v1.model.Faction;
import io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest.v1.model.PlayerScore;
import io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest.v1.model.ScoreDelta;
import io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest.v1.model.ScoresHistoryResponse;
import io.github.temporalrift.game.scoring.infrastructure.adapter.in.rest.v1.model.ScoresResponse;

@RestController
class ScoringController implements ScoringApi {

    private final GetScoresUseCase getScoresUseCase;
    private final GetScoringHistoryUseCase getScoringHistoryUseCase;

    ScoringController(GetScoresUseCase getScoresUseCase, GetScoringHistoryUseCase getScoringHistoryUseCase) {
        this.getScoresUseCase = getScoresUseCase;
        this.getScoringHistoryUseCase = getScoringHistoryUseCase;
    }

    @Override
    public ResponseEntity<ScoresResponse> getScores(UUID gameId) {
        var result = getScoresUseCase.handle(new GetScoresUseCase.Query(gameId));
        var scores =
                result.scores().stream().map(ScoringController::toPlayerScore).toList();
        return ResponseEntity.ok(new ScoresResponse(result.gameId(), result.eraNumber(), scores));
    }

    @Override
    public ResponseEntity<ScoresHistoryResponse> getScoresHistory(UUID gameId) {
        var result = getScoringHistoryUseCase.handle(new GetScoringHistoryUseCase.Query(gameId));
        var history = result.history().stream()
                .map(era -> new EraHistory(
                        era.eraNumber(),
                        era.deltas().stream()
                                .map(delta -> new ScoreDelta(delta.playerId(), delta.pointsDelta(), delta.reason()))
                                .toList()))
                .toList();
        return ResponseEntity.ok(new ScoresHistoryResponse(result.gameId(), history));
    }

    private static PlayerScore toPlayerScore(GetScoresUseCase.PlayerScoreRow row) {
        var playerScore = new PlayerScore(row.playerId(), row.playerName(), row.score());
        if (row.faction() != null) {
            playerScore.faction(Faction.fromValue(row.faction().name()));
        }
        return playerScore;
    }
}
