package io.github.temporalrift.game.session.application.port.in;

import java.util.UUID;

public interface StartGameUseCase {

    Result execute(Command command);

    record Command(UUID lobbyId, UUID requestingPlayerId) {}

    record Result(UUID gameId) {}
}
