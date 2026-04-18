package io.github.temporalrift.game.session.domain.lobby;

public class NotHostException extends RuntimeException {

    public NotHostException() {
        super("Only the host can start the game");
    }
}
