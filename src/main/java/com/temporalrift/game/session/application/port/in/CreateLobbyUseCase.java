package com.temporalrift.game.session.application.port.in;

import java.util.UUID;

public interface CreateLobbyUseCase {

    record Command(UUID playerId, String playerName) {}

    record Result(UUID lobbyId, UUID hostPlayerId, String joinCode) {}

    Result execute(Command command);
}

