package io.github.temporalrift.game.scoring.application.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.application.port.in.GetScoringHistoryUseCase;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoringGameNotFoundException;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringGameVisibilityRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringPlayerRepository;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;

@Service
@Transactional(readOnly = true)
class GetScoringHistoryQueryHandler implements GetScoringHistoryUseCase {

    private final ScoringReadRepository scoringReadRepository;
    private final ScoringPlayerRepository scoringPlayerRepository;
    private final ScoringGameVisibilityRepository visibilityRepository;

    GetScoringHistoryQueryHandler(
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
        var rows = scoringReadRepository.findScoreHistory(query.gameId());
        if (rows.isEmpty()) {
            throw new ScoringGameNotFoundException(query.gameId());
        }

        var factionsRevealed = visibilityRepository.areFactionsRevealed(query.gameId());
        // Rows arrive sorted by era, then playerId, then reason; a LinkedHashMap preserves that
        // ascending era grouping and the within-era ordering for each era's delta list.
        var deltasByEra = new LinkedHashMap<Integer, List<ScoreDeltaRow>>();
        for (var row : rows) {
            deltasByEra
                    .computeIfAbsent(row.eraNumber(), era -> new ArrayList<>())
                    .add(new ScoreDeltaRow(
                            row.playerId(), row.pointsDelta(), visibleReason(row, query.playerId(), factionsRevealed)));
        }

        var history = deltasByEra.entrySet().stream()
                .map(entry -> new EraScoreHistory(entry.getKey(), entry.getValue()))
                .toList();

        return new Result(query.gameId(), history);
    }

    private void requireParticipant(Query query) {
        if (!scoringPlayerRepository.isParticipant(query.gameId(), query.playerId())) {
            throw new ScoringGameNotFoundException(query.gameId());
        }
    }

    private static String visibleReason(
            ScoringReadRepository.ScoreHistoryRow row, UUID playerId, boolean factionsRevealed) {
        return factionsRevealed || row.playerId().equals(playerId)
                ? row.reason().name()
                : null;
    }
}
