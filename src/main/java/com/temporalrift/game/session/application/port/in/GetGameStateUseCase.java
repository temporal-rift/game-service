package com.temporalrift.game.session.application.port.in;

import com.temporalrift.game.session.domain.game.GameStatus;

import java.util.UUID;

public interface GetGameStateUseCase {

    record Query(UUID gameId) {}

    record Result(
            UUID gameId,
            GameStatus status,
            int eraNumber,
            int playerCount,
            int cascadedParadoxCount
    ) {}

    Result execute(Query query);
}

