package io.github.temporalrift.game.scoring.application.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.temporalrift.game.scoring.application.port.in.GetScoringHistoryUseCase;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoringGameNotFoundException;
import io.github.temporalrift.game.scoring.domain.port.out.ScoringReadRepository;

@Service
@Transactional(readOnly = true)
class GetScoringHistoryQueryHandler implements GetScoringHistoryUseCase {

    private final ScoringReadRepository scoringReadRepository;

    GetScoringHistoryQueryHandler(ScoringReadRepository scoringReadRepository) {
        this.scoringReadRepository = scoringReadRepository;
    }

    @Override
    public Result handle(Query query) {
        var rows = scoringReadRepository.findScoreHistory(query.gameId());
        if (rows.isEmpty()) {
            throw new ScoringGameNotFoundException(query.gameId());
        }

        // Rows arrive sorted by era, then playerId, then reason; a LinkedHashMap preserves that
        // ascending era grouping and the within-era ordering for each era's delta list.
        var deltasByEra = new LinkedHashMap<Integer, List<ScoreDeltaRow>>();
        for (var row : rows) {
            deltasByEra
                    .computeIfAbsent(row.eraNumber(), era -> new ArrayList<>())
                    .add(new ScoreDeltaRow(
                            row.playerId(), row.pointsDelta(), row.reason().name()));
        }

        var history = deltasByEra.entrySet().stream()
                .map(entry -> new EraScoreHistory(entry.getKey(), entry.getValue()))
                .toList();

        return new Result(query.gameId(), history);
    }
}
