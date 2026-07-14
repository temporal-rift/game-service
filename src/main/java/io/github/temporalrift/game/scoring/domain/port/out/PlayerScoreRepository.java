package io.github.temporalrift.game.scoring.domain.port.out;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.scoring.domain.playerscore.PlayerScore;

public interface PlayerScoreRepository {

    List<PlayerScore> findAllByGameId(UUID gameId);

    List<PlayerScore> findAllByGameIdWithLock(UUID gameId);

    List<PlayerScore> saveAll(List<PlayerScore> scores);
}
