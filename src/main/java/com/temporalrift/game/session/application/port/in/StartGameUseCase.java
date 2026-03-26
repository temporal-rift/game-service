package com.temporalrift.game.session.application.port.in;

import java.util.UUID;

public interface StartGameUseCase {

    record Command(UUID lobbyId, UUID requestingPlayerId) {}

    record Result(UUID gameId) {}

    Result execute(Command command);
}

