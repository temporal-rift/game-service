package io.github.temporalrift.game.simulation.application.port.in;

import io.github.temporalrift.game.simulation.domain.execution.ExecutionCheckpoint;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContext;

public interface ConfigureSimulationExecutionUseCase {

    ExecutionCheckpoint handle(ExecutionContext context);
}
