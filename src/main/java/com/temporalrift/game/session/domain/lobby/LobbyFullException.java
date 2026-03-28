package com.temporalrift.game.session.domain.lobby;

public class LobbyFullException extends RuntimeException {

    public LobbyFullException() {
        super("Lobby is full");
    }
}
