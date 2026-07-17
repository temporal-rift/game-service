package io.github.temporalrift.game.action.application.saga;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.saga.ActionRoundSagaState;

/**
 * Database-driven safety net for action-round timers. The in-memory timer scheduled at round start
 * is only a latency optimization: it dies with its instance, so the sweep — which any instance can
 * run against the shared saga table — is what guarantees a round always closes.
 *
 * <p>Every instance sweeps concurrently without coordination: the close path's pessimistic locks
 * make duplicate processing converge on a single {@code ActionRoundClosed}, exactly like the
 * timer-vs-submission race. CLOSING sagas are swept too, so a crash mid-close resumes within one
 * sweep interval instead of waiting for a restart.
 */
@Component
@ConditionalOnBean(ActionRoundSagaImpl.class)
class ActionRoundTimerSweep {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundTimerSweep.class);

    private final ActionRoundSagaStateManager stateManager;
    private final ActionRoundTimeoutProcessor timeoutProcessor;
    private final Clock clock;

    ActionRoundTimerSweep(
            ActionRoundSagaStateManager stateManager, ActionRoundTimeoutProcessor timeoutProcessor, Clock clock) {
        this.stateManager = stateManager;
        this.timeoutProcessor = timeoutProcessor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${game.timers.action-round-sweep-ms:1000}")
    void sweep() {
        var now = clock.instant();
        stateManager.findWaitingDueBy(now).forEach(this::process);
        stateManager.findAllClosing().forEach(this::process);
    }

    private void process(ActionRoundSagaState state) {
        // One failing saga must not starve the rest of the sweep batch.
        try {
            timeoutProcessor.handleTimerExpiry(state.sagaId());
        } catch (RuntimeException ex) {
            log.error("Timer sweep failed for saga {} (game {})", state.sagaId(), state.gameId(), ex);
        }
    }
}
