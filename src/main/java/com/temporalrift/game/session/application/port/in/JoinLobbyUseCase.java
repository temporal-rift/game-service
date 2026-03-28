package com.temporalrift.game.session.application.port.in;

import java.util.List;
import java.util.UUID;

public interface JoinLobbyUseCase {

    Result execute(Command command);

    record Command(UUID lobbyId, UUID playerId, String playerName) {}

    record PlayerSummary(UUID playerId, String playerName, boolean isHost) {}

    record Result(UUID lobbyId, UUID playerId, List<PlayerSummary> currentPlayers) {}
}
