package io.github.temporalrift.game.session.domain.lobby;

import java.util.UUID;

public class LobbyAccessDeniedException extends RuntimeException {

    public LobbyAccessDeniedException(UUID lobbyId) {
        super("Player is not a member of lobby: " + lobbyId);
    }
}
