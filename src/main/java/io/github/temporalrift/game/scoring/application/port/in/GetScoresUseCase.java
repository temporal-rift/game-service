package io.github.temporalrift.game.scoring.application.port.in;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.temporalrift.game.shared.Faction;

public interface GetScoresUseCase {

    Result handle(Query query);

    record Query(UUID gameId) {

        public Query {
            Objects.requireNonNull(gameId, "gameId must not be null");
        }
    }

    record Result(UUID gameId, int eraNumber, List<PlayerScoreRow> scores) {

        public Result(UUID gameId, int eraNumber, List<PlayerScoreRow> scores) {
            this.gameId = gameId;
            this.eraNumber = eraNumber;
            this.scores = List.copyOf(scores);
        }
    }

    /** A player's current total score. {@code faction} is null while factions are hidden. */
    record PlayerScoreRow(UUID playerId, String playerName, int score, Faction faction) {}
}
