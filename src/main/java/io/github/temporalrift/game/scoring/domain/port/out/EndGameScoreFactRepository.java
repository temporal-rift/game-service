package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

/**
 * Atomically claims one game-end scoring fact for a player.
 *
 * <p>Implementations must return {@code true} only to the first caller for the same game, player,
 * and reason. This makes at-least-once delivery of the final faction reveal safe.
 */
public interface EndGameScoreFactRepository {

    boolean claim(UUID gameId, UUID playerId, ScoreReason reason);
}
