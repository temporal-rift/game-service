package io.github.temporalrift.game.session.domain.lobby;

import java.util.UUID;

public class PlayerNotInLobbyException extends RuntimeException {

    public PlayerNotInLobbyException(UUID playerId) {
        super("Player not in lobby: " + playerId);
    }
}
