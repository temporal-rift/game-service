package io.github.temporalrift.game.session.application.port.in;

import java.util.UUID;

import io.github.temporalrift.game.session.domain.game.GameStatus;

public interface GetGameStateUseCase {

    Result handle(Query query);

    record Query(UUID gameId) {}

    record Result(UUID gameId, GameStatus status, int eraNumber, int playerCount, int cascadedParadoxCount) {}
}
