package io.github.temporalrift.game.simulation.domain.execution;

/** No context is open, but the deployment still holds gameplay of an earlier one, so none can be opened. */
public class ExecutionStateNotCleanException extends RuntimeException {

    public ExecutionStateNotCleanException(String message) {
        super(message);
    }
}
