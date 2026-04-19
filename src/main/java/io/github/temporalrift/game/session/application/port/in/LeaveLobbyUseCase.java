package io.github.temporalrift.game.session.application.port.in;

import java.util.UUID;

public interface LeaveLobbyUseCase {

    Result handle(Command command);

    record Command(UUID lobbyId, UUID playerId) {}

    @SuppressWarnings("java:S2094")
    record Result() {}
}
