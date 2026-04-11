package io.github.temporalrift.game.session.domain.lobby;

public class FactionNotAssignedException extends RuntimeException {

    public FactionNotAssignedException() {
        super("Not all players have a faction assigned");
    }
}
