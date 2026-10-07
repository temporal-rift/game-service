package io.github.temporalrift.game.simulation.application.command;

import static io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.shared.domain.event.LogicalClockAdvanced;
import io.github.temporalrift.game.simulation.domain.execution.ClockAdvance;
import io.github.temporalrift.game.simulation.domain.execution.ClockOperation;
import io.github.temporalrift.game.simulation.domain.execution.ClockRegressionException;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.execution.IdempotencyConflictException;
import io.github.temporalrift.game.simulation.domain.execution.StaleExecutionRevisionException;
import io.github.temporalrift.game.simulation.domain.port.out.ClockOperationRepository;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.LogicalClockControl;

@ExtendWith(MockitoExtension.class)
class AdvanceSimulationClockCommandHandlerTest {

    private static final UUID OPERATION = UUID.fromString("0a0a0a0a-0000-4000-8000-000000000001");

    @Mock
    ExecutionRepository executions;

    @Mock
    LogicalClockControl clockControl;

    @Mock
    ApplicationEventPublisher events;

    @Mock
    TransactionTemplate transaction;

    private final Map<UUID, ClockOperation> recorded = new HashMap<>();
    private Execution stored = Execution.configure(ExecutionContextTestData.context("42"));
    private AdvanceSimulationClockCommandHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient()
                .when(transaction.execute(any()))
                .thenAnswer(
                        invocation -> ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
        lenient()
                .doAnswer(invocation -> {
                    invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
                    return null;
                })
                .when(transaction)
                .executeWithoutResult(any());
        lenient().when(executions.findWithLock()).thenAnswer(invocation -> Optional.of(stored));
        lenient()
                .doAnswer(invocation -> {
                    stored = invocation.getArgument(0);
                    return null;
                })
                .when(executions)
                .update(any());
        var operations = new ClockOperationRepository() {
            @Override
            public Optional<ClockOperation> find(UUID operationId) {
                return Optional.ofNullable(recorded.get(operationId));
            }

            @Override
            public void save(ClockOperation operation) {
                recorded.put(operation.request().operationId(), operation);
            }
        };
        handler = new AdvanceSimulationClockCommandHandler(
                executions,
                operations,
                clockControl,
                Clock.fixed(START.plusSeconds(61), ZoneOffset.UTC),
                events,
                transaction);
    }

    @Test
    @DisplayName("an advance records the operation, moves the clock and runs the due-time handlers")
    void handle_newOperation_advancesAndAnnounces() {
        var acknowledgement = handler.handle(new ClockAdvance(OPERATION, 0, START.plusSeconds(61)));

        assertThat(acknowledgement.appliedRevision()).isEqualTo(1);
        assertThat(stored.logicalTime()).isEqualTo(START.plusSeconds(61));
        then(clockControl).should().advanceTo(START.plusSeconds(61));
        then(events).should().publishEvent(new LogicalClockAdvanced(START.plusSeconds(61)));
    }

    @Test
    @DisplayName("a repeat after a lost acknowledgement returns the original one and re-runs the handlers")
    void handle_repeatedOperation_returnsOriginalAcknowledgementAndReRunsHandlers() {
        var request = new ClockAdvance(OPERATION, 0, START.plusSeconds(61));
        var original = handler.handle(request);

        var repeat = handler.handle(request);

        assertThat(repeat).isEqualTo(original);
        assertThat(stored.revision()).isEqualTo(1);
        then(events).should(times(2)).publishEvent(any(LogicalClockAdvanced.class));
    }

    @Test
    @DisplayName("a repeated operation identifier with a changed body is an idempotency conflict")
    void handle_changedBody_isIdempotencyConflict() {
        handler.handle(new ClockAdvance(OPERATION, 0, START.plusSeconds(61)));

        assertThatThrownBy(() -> handler.handle(new ClockAdvance(OPERATION, 0, START.plusSeconds(90))))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("a stale revision and backward time are rejected without moving the clock or running handlers")
    void handle_staleOrBackward_leavesTimeAlone() {
        assertThatThrownBy(() -> handler.handle(new ClockAdvance(OPERATION, 5, START.plusSeconds(61))))
                .isInstanceOf(StaleExecutionRevisionException.class);
        assertThatThrownBy(() -> handler.handle(new ClockAdvance(UUID.randomUUID(), 0, START.minusSeconds(1))))
                .isInstanceOf(ClockRegressionException.class);

        then(clockControl).shouldHaveNoInteractions();
        then(events).shouldHaveNoInteractions();
        assertThat(stored.revision()).isZero();
    }

    @Test
    @DisplayName("an advance before any execution is configured is rejected")
    void handle_unconfigured_isRejected() {
        given(executions.findWithLock()).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ClockAdvance(OPERATION, 0, START)))
                .isInstanceOf(ExecutionNotConfiguredException.class);
    }
}
