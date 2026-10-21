package io.github.temporalrift.game.simulation.application.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

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

import io.github.temporalrift.game.shared.domain.event.ExecutionClosed;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextConflictException;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextTestData;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.port.out.ClockOperationRepository;
import io.github.temporalrift.game.simulation.domain.port.out.EntropyDecisionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.GameplayDiscard;
import io.github.temporalrift.game.simulation.domain.port.out.LogicalClockControl;

@ExtendWith(MockitoExtension.class)
class CloseSimulationExecutionCommandHandlerTest {

    private static final UUID OPEN = ExecutionContextTestData.CASE_KEY;
    private static final UUID EARLIER = UUID.fromString("c0ffee00-0000-4000-8000-000000000009");
    private static final UUID UNRELATED = UUID.fromString("c0ffee00-0000-4000-8000-0000000000ff");
    private static final Execution EXECUTION = Execution.configure(ExecutionContextTestData.context("42"));

    @Mock
    ExecutionRepository executions;

    @Mock
    ClockOperationRepository operations;

    @Mock
    EntropyDecisionRepository decisions;

    @Mock
    GameplayDiscard gameplay;

    @Mock
    LogicalClockControl clock;

    @Mock
    ApplicationEventPublisher events;

    @Mock
    TransactionTemplate transaction;

    private CloseSimulationExecutionCommandHandler handler;

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
        handler = new CloseSimulationExecutionCommandHandler(
                executions, operations, decisions, gameplay, clock, events, transaction);
    }

    @Test
    @DisplayName("closing the open context discards its work and state, then drops the in-process views")
    void handle_openContext_discardsEverythingAndAnnounces() {
        given(executions.findWithLock()).willReturn(Optional.of(EXECUTION));

        handler.handle(OPEN);

        var order = inOrder(gameplay, operations, decisions, executions, clock, events);
        order.verify(gameplay).discardAll();
        order.verify(operations).clear();
        order.verify(decisions).clear();
        order.verify(executions).close(OPEN);
        order.verify(clock).reset();
        order.verify(events).publishEvent(new ExecutionClosed(OPEN));
    }

    @Test
    @DisplayName("the most recently closed case is closed again as a no-op, also while the next context is open")
    void handle_lastClosedCase_isNoOp() {
        given(executions.findWithLock()).willReturn(Optional.of(EXECUTION));
        given(executions.lastClosedCaseKey()).willReturn(Optional.of(EARLIER));

        handler.handle(EARLIER);

        verifyNoInteractions(gameplay, operations, decisions, clock, events);
        then(executions).should(never()).close(any());
    }

    @Test
    @DisplayName("repeating the close with no context open discards nothing but repeats the in-process cleanup")
    void handle_noContextOpen_lastClosedCase_repeatsTheCleanup() {
        given(executions.findWithLock()).willReturn(Optional.empty());
        given(executions.lastClosedCaseKey()).willReturn(Optional.of(EARLIER));

        handler.handle(EARLIER);

        verifyNoInteractions(gameplay, operations, decisions);
        then(clock).should().reset();
        then(events).should().publishEvent(new ExecutionClosed(EARLIER));
    }

    @Test
    @DisplayName("a case that is neither open nor the last closed is a conflict that changes nothing")
    void handle_unrelatedCase_isConflict() {
        given(executions.findWithLock()).willReturn(Optional.of(EXECUTION));
        given(executions.lastClosedCaseKey()).willReturn(Optional.of(EARLIER));

        assertThatThrownBy(() -> handler.handle(UNRELATED)).isInstanceOf(ExecutionContextConflictException.class);

        verifyNoInteractions(gameplay, operations, decisions, clock, events);
    }

    @Test
    @DisplayName("with no context open, an unrelated case is a conflict")
    void handle_noContextOpen_unrelatedCase_isConflict() {
        given(executions.findWithLock()).willReturn(Optional.empty());
        given(executions.lastClosedCaseKey()).willReturn(Optional.of(EARLIER));

        assertThatThrownBy(() -> handler.handle(UNRELATED)).isInstanceOf(ExecutionContextConflictException.class);
    }

    @Test
    @DisplayName("with no context open and none ever closed, the lane is not configured")
    void handle_neverConfigured_isNotConfigured() {
        given(executions.findWithLock()).willReturn(Optional.empty());
        given(executions.lastClosedCaseKey()).willReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(OPEN)).isInstanceOf(ExecutionNotConfiguredException.class);

        verifyNoInteractions(gameplay, clock, events);
    }

    @Test
    @DisplayName("a close whose transaction fails announces nothing and leaves the clock alone")
    void handle_discardFails_announcesNothing() {
        given(executions.findWithLock()).willReturn(Optional.of(EXECUTION));
        doThrow(new IllegalStateException("truncate failed")).when(gameplay).discardAll();

        assertThatThrownBy(() -> handler.handle(OPEN)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(clock, events);
    }
}
