package io.github.temporalrift.game.action.domain.actionround;

public class ActionRoundClosedException extends RuntimeException {

    public ActionRoundClosedException() {
        super("Action round is already closed");
    }
}
