package io.github.temporalrift.game.scoring.application.query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.application.port.in.GetScoresUseCase;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoringGameNotFoundException;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository.CurrentScoreRow;
import io.github.temporalrift.game.shared.domain.model.Faction;

@Service
@Transactional(readOnly = true)
class GetScoresQueryHandler implements GetScoresUseCase {

    private final ScoringReadRepository scoringReadRepository;
    private final ScoringPlayerRepository scoringPlayerRepository;
    private final ScoringGameVisibilityRepository visibilityRepository;

    GetScoresQueryHandler(
            ScoringReadRepository scoringReadRepository,
            ScoringPlayerRepository scoringPlayerRepository,
            ScoringGameVisibilityRepository visibilityRepository) {
        this.scoringReadRepository = scoringReadRepository;
        this.scoringPlayerRepository = scoringPlayerRepository;
        this.visibilityRepository = visibilityRepository;
    }

    @Override
    public Result handle(Query query) {
        requireParticipant(query);
        var rows = scoringReadRepository.findCurrentScores(query.gameId());
        if (rows.isEmpty()) {
            throw new ScoringGameNotFoundException(query.gameId());
        }

        var factionsRevealed = visibilityRepository.areFactionsRevealed(query.gameId());
        var eraNumber = rows.stream().mapToInt(CurrentScoreRow::eraNumber).max().orElse(0);

        var scores = rows.stream()
                .map(row -> new PlayerScoreRow(
                        row.playerId(), row.playerName(), row.score(), visibleFaction(row.faction(), factionsRevealed)))
                .toList();

        return new Result(query.gameId(), eraNumber, scores);
    }

    private void requireParticipant(Query query) {
        if (!scoringPlayerRepository.isParticipant(query.gameId(), query.playerId())) {
            throw new ScoringGameNotFoundException(query.gameId());
        }
    }

    private static Faction visibleFaction(Faction faction, boolean factionsRevealed) {
        return factionsRevealed ? faction : null;
    }
}
