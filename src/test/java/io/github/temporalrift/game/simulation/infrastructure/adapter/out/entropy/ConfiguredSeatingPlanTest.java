package io.github.temporalrift.game.simulation.infrastructure.adapter.out.entropy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.port.out.SeatingPlan.SeatAssignment;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;

@ExtendWith(MockitoExtension.class)
class ConfiguredSeatingPlanTest {

    @Mock
    ExecutionRepository executions;

    @Test
    @DisplayName("the plan offers the configured seats in order")
    void configuredSeats_returnsTheConfiguredAssignments() {
        given(executions.find()).willReturn(Optional.of(Execution.configure(ExecutionContextTestData.context("42"))));

        var seats = new ConfiguredSeatingPlan(new PinnedExecutionContext(executions))
                .configuredSeats()
                .orElseThrow();

        assertThat(seats)
                .containsExactly(
                        new SeatAssignment(0, ExecutionContextTestData.PLAYER_0, Faction.ERASERS),
                        new SeatAssignment(1, ExecutionContextTestData.PLAYER_1, Faction.PROPHETS),
                        new SeatAssignment(2, ExecutionContextTestData.PLAYER_2, Faction.WEAVERS));
    }
}
