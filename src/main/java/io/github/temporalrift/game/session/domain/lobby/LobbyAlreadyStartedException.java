package io.github.temporalrift.game.session.domain.lobby;

public class LobbyAlreadyStartedException extends RuntimeException {

    public LobbyAlreadyStartedException() {
        super("Lobby has already started");
    }
}
