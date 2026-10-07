package io.github.temporalrift.game.simulation.application.query;

import static io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe;
import io.github.temporalrift.game.shared.domain.port.out.ExecutionProbe.Observation;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionState;
import io.github.temporalrift.game.simulation.domain.execution.SourceWatermark;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.OutboxProbe;
import io.github.temporalrift.game.simulation.domain.port.out.SourceWatermarks;

@ExtendWith(MockitoExtension.class)
class ExecutionCheckpointAssemblerTest {

    private static final UUID GAME = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
    private static final Execution EXECUTION = Execution.configure(ExecutionContextTestData.context("42"));

    @Mock
    ExecutionProbe sessionProbe;

    @Mock
    ExecutionProbe actionProbe;

    @Mock
    OutboxProbe outbox;

    @Mock
    SourceWatermarks watermarks;

    @Mock
    ExecutionRepository executions;

    private ExecutionCheckpointAssembler assembler() {
        return new ExecutionCheckpointAssembler(List.of(sessionProbe, actionProbe), outbox, watermarks);
    }

    @Test
    @DisplayName("before any game exists the lane is ready and drained")
    void assemble_noGame_isReadyAndDrained() {
        given(sessionProbe.observe(START)).willReturn(new Observation(null, false, 0, 0, null));
        given(actionProbe.observe(START)).willReturn(new Observation(null, false, 0, 0, null));
        given(outbox.pendingPublications()).willReturn(0);
        given(watermarks.current()).willReturn(List.of());

        var checkpoint = assembler().assemble(EXECUTION);

        assertThat(checkpoint.state()).isEqualTo(ExecutionState.READY);
        assertThat(checkpoint.drained()).isTrue();
        assertThat(checkpoint.gameId()).isNull();
        assertThat(checkpoint.nextDeadline()).isNull();
        assertThat(checkpoint.revision()).isZero();
    }

    @Test
    @DisplayName("pending work is summed across modules, the earliest deadline wins and drained is false")
    void assemble_activeGame_sumsWorkAndKeepsEarliestDeadline() {
        var early = START.plusSeconds(10);
        given(sessionProbe.observe(START)).willReturn(new Observation(GAME, false, 1, 2, START.plusSeconds(60)));
        given(actionProbe.observe(START)).willReturn(new Observation(null, false, 3, 0, early));
        given(outbox.pendingPublications()).willReturn(4);
        given(watermarks.current()).willReturn(List.of(new SourceWatermark("game-service", "game.events", 0, 5)));

        var checkpoint = assembler().assemble(EXECUTION);

        assertThat(checkpoint.state()).isEqualTo(ExecutionState.ACTIVE);
        assertThat(checkpoint.gameId()).isEqualTo(GAME);
        assertThat(checkpoint.dueTimersPending()).isEqualTo(4);
        assertThat(checkpoint.continuationsPending()).isEqualTo(2);
        assertThat(checkpoint.outboxPending()).isEqualTo(4);
        assertThat(checkpoint.nextDeadline()).isEqualTo(early);
        assertThat(checkpoint.drained()).isFalse();
        assertThat(checkpoint.sourceWatermarks()).hasSize(1);
    }

    @Test
    @DisplayName("an ended game is terminal")
    void assemble_endedGame_isTerminal() {
        given(sessionProbe.observe(START)).willReturn(new Observation(GAME, true, 0, 0, null));
        given(actionProbe.observe(START)).willReturn(new Observation(null, false, 0, 0, null));
        given(outbox.pendingPublications()).willReturn(0);
        given(watermarks.current()).willReturn(List.of());

        assertThat(assembler().assemble(EXECUTION).state()).isEqualTo(ExecutionState.TERMINAL);
    }

    @Test
    @DisplayName("the checkpoint query reads the configured execution and rejects an unconfigured one")
    void queryHandler_readsExecutionOrRejects() {
        lenient().when(sessionProbe.observe(any(Instant.class))).thenReturn(new Observation(null, false, 0, 0, null));
        lenient().when(actionProbe.observe(any(Instant.class))).thenReturn(new Observation(null, false, 0, 0, null));
        given(outbox.pendingPublications()).willReturn(0);
        given(watermarks.current()).willReturn(List.of());
        var query = new GetSimulationCheckpointQueryHandler(executions, assembler());
        given(executions.find()).willReturn(Optional.of(EXECUTION)).willReturn(Optional.empty());

        assertThat(query.handle().caseKey()).isEqualTo(EXECUTION.context().caseKey());
        assertThatThrownBy(query::handle).isInstanceOf(ExecutionNotConfiguredException.class);
    }
}
