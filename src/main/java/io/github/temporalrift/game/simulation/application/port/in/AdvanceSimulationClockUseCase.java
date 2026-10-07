package io.github.temporalrift.game.simulation.application.port.in;

import io.github.temporalrift.game.simulation.domain.execution.ClockAcknowledgement;
import io.github.temporalrift.game.simulation.domain.execution.ClockAdvance;

public interface AdvanceSimulationClockUseCase {

    ClockAcknowledgement handle(ClockAdvance advance);
}
