package com.temporalrift.game.session.application.port.in;

import java.util.UUID;

public interface LeaveLobbyUseCase {

    record Command(UUID lobbyId, UUID playerId) {}

    void execute(Command command);
}

