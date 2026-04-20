package io.github.temporalrift.game.session.domain.lobby;

public class NotLobbyHostException extends RuntimeException {

    public NotLobbyHostException() {
        super("Only the host can start the game");
    }
}
