package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.Faction;

/**
 * Read-only projection of scoring-owned persisted state for the score REST API. {@code faction} is the
 * real stored faction; visibility filtering happens in the query handler, not here.
 */
public interface ScoringReadRepository {

    List<CurrentScoreRow> findCurrentScores(UUID gameId);

    List<ScoreHistoryRow> findScoreHistory(UUID gameId);

    record CurrentScoreRow(UUID gameId, int eraNumber, UUID playerId, String playerName, int score, Faction faction) {}

    record ScoreHistoryRow(UUID gameId, int eraNumber, UUID playerId, int pointsDelta, ScoreReason reason) {}
}
