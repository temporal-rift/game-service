package com.temporalrift.game.session.application.port.in;

import java.util.UUID;

public interface LeaveLobbyUseCase {

    void execute(Command command);

    record Command(UUID lobbyId, UUID playerId) {}
}
