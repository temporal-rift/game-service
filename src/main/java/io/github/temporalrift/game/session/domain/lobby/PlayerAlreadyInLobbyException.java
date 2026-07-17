package io.github.temporalrift.game.session.domain.lobby;

import java.util.UUID;

public class PlayerAlreadyInLobbyException extends RuntimeException {

    public PlayerAlreadyInLobbyException(UUID playerId) {
        super("Player " + playerId + " is already in the lobby");
    }
}
