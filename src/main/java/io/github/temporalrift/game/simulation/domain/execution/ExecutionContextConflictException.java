package io.github.temporalrift.game.simulation.domain.execution;

public class ExecutionContextConflictException extends RuntimeException {

    public ExecutionContextConflictException(String message) {
        super(message);
    }
}
