package io.github.temporalrift.game.simulation.domain.execution;

public class InvalidExecutionContextException extends RuntimeException {

    public InvalidExecutionContextException(String message) {
        super(message);
    }
}
