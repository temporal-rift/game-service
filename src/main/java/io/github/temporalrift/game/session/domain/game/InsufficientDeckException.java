package io.github.temporalrift.game.session.domain.game;

public class InsufficientDeckException extends RuntimeException {

    public InsufficientDeckException() {
        super("Not enough events remaining in deck");
    }
}
