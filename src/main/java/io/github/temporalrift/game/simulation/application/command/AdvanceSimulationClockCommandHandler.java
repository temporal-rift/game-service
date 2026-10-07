package io.github.temporalrift.game.simulation.application.command;

import java.time.Clock;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.shared.domain.event.LogicalClockAdvanced;
import io.github.temporalrift.game.simulation.application.port.in.AdvanceSimulationClockUseCase;
import io.github.temporalrift.game.simulation.domain.execution.ClockAcknowledgement;
import io.github.temporalrift.game.simulation.domain.execution.ClockAdvance;
import io.github.temporalrift.game.simulation.domain.execution.ExecutionNotConfiguredException;
import io.github.temporalrift.game.simulation.domain.port.out.ClockOperationRepository;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;
import io.github.temporalrift.game.simulation.domain.port.out.LogicalClockControl;

@Service
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class AdvanceSimulationClockCommandHandler implements AdvanceSimulationClockUseCase {

    private final ExecutionRepository executions;
    private final ClockOperationRepository operations;
    private final LogicalClockControl clockControl;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;

    AdvanceSimulationClockCommandHandler(
            ExecutionRepository executions,
            ClockOperationRepository operations,
            LogicalClockControl clockControl,
            Clock clock,
            ApplicationEventPublisher events,
            TransactionTemplate transaction) {
        this.executions = executions;
        this.operations = operations;
        this.clockControl = clockControl;
        this.clock = clock;
        this.events = events;
        this.transaction = transaction;
    }

    /**
     * Due-time handlers run asynchronously after the announcement commits, so completion is observed through the
     * checkpoint barrier. A repeated operation announces again: a crash between the advance and its announcement
     * must not leave the caller holding an acknowledgement for work that never ran.
     */
    @Override
    public ClockAcknowledgement handle(ClockAdvance advance) {
        var acknowledgement = Objects.requireNonNull(transaction.execute(status -> applyAdvance(advance)));
        clockControl.advanceTo(acknowledgement.logicalTime());
        transaction.executeWithoutResult(status -> events.publishEvent(new LogicalClockAdvanced(clock.instant())));
        return acknowledgement;
    }

    private ClockAcknowledgement applyAdvance(ClockAdvance advance) {
        var execution = executions.findWithLock().orElseThrow(ExecutionNotConfiguredException::new);
        var recorded = operations.find(advance.operationId()).orElse(null);
        var outcome = execution.advance(advance, recorded);
        if (outcome.applied()) {
            executions.update(outcome.execution());
            operations.save(outcome.operation());
        }
        return outcome.operation().acknowledgement();
    }
}
