package io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.github.temporalrift.game.simulation.application.port.in.AdvanceSimulationClockUseCase;
import io.github.temporalrift.game.simulation.application.port.in.ConfigureSimulationExecutionUseCase;
import io.github.temporalrift.game.simulation.application.port.in.GetSimulationCheckpointUseCase;
import io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.SimulationExecutionApi;
import io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ClockAcknowledgement;
import io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ClockAdvance;
import io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ExecutionCheckpoint;
import io.github.temporalrift.game.simulation.infrastructure.adapter.in.rest.v1.model.ExecutionContext;

@RestController
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class SimulationControlController implements SimulationExecutionApi {

    private final ConfigureSimulationExecutionUseCase configureExecution;
    private final GetSimulationCheckpointUseCase getCheckpoint;
    private final AdvanceSimulationClockUseCase advanceClock;

    SimulationControlController(
            ConfigureSimulationExecutionUseCase configureExecution,
            GetSimulationCheckpointUseCase getCheckpoint,
            AdvanceSimulationClockUseCase advanceClock) {
        this.configureExecution = configureExecution;
        this.getCheckpoint = getCheckpoint;
        this.advanceClock = advanceClock;
    }

    @Override
    public ResponseEntity<ExecutionCheckpoint> configureSimulationExecution(ExecutionContext executionContext) {
        var checkpoint = configureExecution.handle(SimulationControlMapper.toDomain(executionContext));
        return ResponseEntity.ok(SimulationControlMapper.toModel(checkpoint));
    }

    @Override
    public ResponseEntity<ExecutionCheckpoint> getSimulationCheckpoint() {
        return ResponseEntity.ok(SimulationControlMapper.toModel(getCheckpoint.handle()));
    }

    @Override
    public ResponseEntity<ClockAcknowledgement> advanceSimulationClock(ClockAdvance clockAdvance) {
        var acknowledgement = advanceClock.handle(SimulationControlMapper.toDomain(clockAdvance));
        return ResponseEntity.ok(SimulationControlMapper.toModel(acknowledgement));
    }
}
