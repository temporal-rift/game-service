package io.github.temporalrift.game.simulation.domain.execution;

public class InvalidClockAdvanceException extends RuntimeException {

    public InvalidClockAdvanceException(String message) {
        super(message);
    }
}
