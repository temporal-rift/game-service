package io.github.temporalrift.game.session.domain.game;

public class GameAlreadyOverException extends RuntimeException {

    public GameAlreadyOverException() {
        super("Game is already over");
    }
}
