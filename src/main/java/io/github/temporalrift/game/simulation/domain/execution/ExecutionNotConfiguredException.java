package io.github.temporalrift.game.simulation.domain.execution;

public class ExecutionNotConfiguredException extends RuntimeException {

    public ExecutionNotConfiguredException() {
        super("No simulation execution has been configured");
    }
}
