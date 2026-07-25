package io.github.temporalrift.game.scoring.application.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface GetScoringHistoryUseCase {

    Result handle(Query query);

    record Query(UUID gameId) {

        public Query {
            Objects.requireNonNull(gameId, "gameId must not be null");
        }
    }

    record Result(UUID gameId, List<EraScoreHistory> history) {

        public Result(UUID gameId, List<EraScoreHistory> history) {
            this.gameId = gameId;
            this.history = List.copyOf(history);
        }
    }

    record EraScoreHistory(int eraNumber, List<ScoreDeltaRow> deltas) {

        public EraScoreHistory(int eraNumber, List<ScoreDeltaRow> deltas) {
            this.eraNumber = eraNumber;
            this.deltas = List.copyOf(deltas);
        }
    }

    record ScoreDeltaRow(UUID playerId, int pointsDelta, String reason) {}
}
