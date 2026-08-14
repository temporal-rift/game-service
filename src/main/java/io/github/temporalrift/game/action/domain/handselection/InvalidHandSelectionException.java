package io.github.temporalrift.game.action.domain.handselection;

public class InvalidHandSelectionException extends RuntimeException {
    public InvalidHandSelectionException() {
        super("The selected cards must be exactly five cards from the pending deal");
    }
}
