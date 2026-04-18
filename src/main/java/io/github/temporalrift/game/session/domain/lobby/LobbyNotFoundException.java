package io.github.temporalrift.game.session.domain.lobby;

import java.util.UUID;

public class LobbyNotFoundException extends RuntimeException {

    public LobbyNotFoundException(UUID lobbyId) {
        super("Lobby not found: " + lobbyId);
    }
}
