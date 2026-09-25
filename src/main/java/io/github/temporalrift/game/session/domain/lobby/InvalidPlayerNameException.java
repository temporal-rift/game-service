package io.github.temporalrift.game.session.domain.lobby;

public class InvalidPlayerNameException extends IllegalArgumentException {

    public InvalidPlayerNameException(String detail) {
        super(detail);
    }
}
