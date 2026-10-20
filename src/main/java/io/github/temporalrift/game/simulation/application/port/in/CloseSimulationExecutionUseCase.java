package io.github.temporalrift.game.simulation.application.port.in;

import java.util.UUID;

public interface CloseSimulationExecutionUseCase {

    void handle(UUID caseKey);
}
