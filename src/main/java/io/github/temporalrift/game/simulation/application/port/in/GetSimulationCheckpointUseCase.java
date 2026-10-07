package io.github.temporalrift.game.simulation.application.port.in;

import io.github.temporalrift.game.simulation.domain.execution.ExecutionCheckpoint;

public interface GetSimulationCheckpointUseCase {

    ExecutionCheckpoint handle();
}
