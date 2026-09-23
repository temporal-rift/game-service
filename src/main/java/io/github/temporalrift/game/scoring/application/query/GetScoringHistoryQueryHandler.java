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
        //
        // Visibility boundary: before the final faction reveal, reason-level magnitudes are
        // faction-identifying (the public scoring table is asymmetric, e.g. +3 Annihilate implies
        // Erasers, +10 chain completion implies Weavers), so each opponent's rows within an era are
        // combined into a single per-player net entry with no reason. Totals and per-era net changes
        // stay public as intended clues for win-condition tracking; only the reason-level breakdown
        // is withheld until reveal. The caller's own rows always keep reason-level detail, and after
        // the reveal every row is returned unchanged for full auditability.
        var deltasByEra = new LinkedHashMap<Integer, List<ScoreDeltaRow>>();
        // Pre-reveal opponent aggregation slots: era -> (playerId -> index in that era's delta list).
        var opponentSlotByEra = new LinkedHashMap<Integer, LinkedHashMap<UUID, Integer>>();
        for (var row : rows) {
            var deltas = deltasByEra.computeIfAbsent(row.eraNumber(), era -> new ArrayList<>());
            if (factionsRevealed || row.playerId().equals(query.playerId())) {
                deltas.add(new ScoreDeltaRow(
                        row.playerId(), row.pointsDelta(), row.reason().name()));
            } else {
                var slots = opponentSlotByEra.computeIfAbsent(row.eraNumber(), era -> new LinkedHashMap<>());
                var slot = slots.get(row.playerId());
                if (slot == null) {
                    slots.put(row.playerId(), deltas.size());
                    deltas.add(new ScoreDeltaRow(row.playerId(), row.pointsDelta(), null));
                } else {
                    var existing = deltas.get(slot);
                    deltas.set(
                            slot,
                            new ScoreDeltaRow(existing.playerId(), existing.pointsDelta() + row.pointsDelta(), null));
                }
            }
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
}
