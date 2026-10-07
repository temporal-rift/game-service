package io.github.temporalrift.game.simulation.domain.execution;

public class StaleExecutionRevisionException extends RuntimeException {

    public StaleExecutionRevisionException(long expected, long actual) {
        super("Expected revision " + expected + " but the execution is at revision " + actual);
    }
}
