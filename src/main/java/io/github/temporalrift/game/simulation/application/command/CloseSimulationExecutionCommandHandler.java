package io.github.temporalrift.game.simulation.application.command;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.shared.domain.event.ExecutionClosed;
import io.github.temporalrift.game.simulation.application.port.in.CloseSimulationExecutionUseCase;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionContextConflictException;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.port.out.ClockOperationRepository;
import io.github.temporalrift.game.simulation.domain.port.out.EntropyDecisionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.GameplayDiscard;
import io.github.temporalrift.game.simulation.domain.port.out.LogicalClockControl;

@Service
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class CloseSimulationExecutionCommandHandler implements CloseSimulationExecutionUseCase {

    private final ExecutionRepository executions;
    private final ClockOperationRepository operations;
    private final EntropyDecisionRepository decisions;
    private final GameplayDiscard gameplay;
    private final LogicalClockControl clock;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    CloseSimulationExecutionCommandHandler(
            ExecutionRepository executions,
            ClockOperationRepository operations,
            EntropyDecisionRepository decisions,
            GameplayDiscard gameplay,
            LogicalClockControl clock,
            ApplicationEventPublisher events,
            TransactionTemplate transaction) {
        this.executions = executions;
        this.operations = operations;
        this.decisions = decisions;
        this.gameplay = gameplay;
        this.clock = clock;
        this.events = events;
        this.transaction = transaction;
    }

    /**
     * The context's work is discarded in the transaction that closes it, so no handler can act on it afterwards; the
     * in-process views of the context are dropped once that transaction committed, and the announcement runs in a
     * transaction of its own so module listeners receive it after commit.
     */
    @Override
    public void handle(UUID caseKey) {
        var closed = Boolean.TRUE.equals(transaction.execute(status -> close(caseKey)));
        if (closed) {
            clock.reset();
            transaction.executeWithoutResult(status -> events.publishEvent(new ExecutionClosed(caseKey)));
        }
    }

    private boolean close(UUID caseKey) {
        var open = executions.findWithLock();
        if (open.isPresent()) {
            return closeOpen(open.get(), caseKey);
        }
        var lastClosed = executions.lastClosedCaseKey().orElseThrow(ExecutionNotConfiguredException::new);
        requireClosedEarlier(lastClosed, caseKey);
        // With nothing open, a repeat completes the cleanup a failed announcement may have left undone.
        return true;
    }

    private boolean closeOpen(Execution open, UUID caseKey) {
        if (open.context().caseKey().equals(caseKey)) {
            gameplay.discardAll();
            operations.clear();
            decisions.clear();
            executions.close(caseKey);
            return true;
        }
        requireClosedEarlier(executions.lastClosedCaseKey().orElse(null), caseKey);
        return false;
    }

    private static void requireClosedEarlier(UUID lastClosed, UUID caseKey) {
        if (!caseKey.equals(lastClosed)) {
            throw new ExecutionContextConflictException("The case is neither the open context nor the last closed one");
        }
    }
}
