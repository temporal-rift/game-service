package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the in-memory scheduling concerns for action-round timers.
 *
 * <p>The saga computes business state and the timeout processor owns transactional execution. This
 * class only deals with when a timeout callback should fire and ensures a timer is not scheduled
 * until the surrounding transaction commits successfully.
 */
@Component
@ConditionalOnBean(ActionRoundSagaImpl.class)
class ActionRoundTimerScheduler {

    private final TaskScheduler taskScheduler;
    private final ActionRoundTimeoutProcessor timeoutProcessor;
    private final ActionRoundTimerRegistry timerRegistry;

    ActionRoundTimerScheduler(
            @Qualifier("actionTaskScheduler") TaskScheduler taskScheduler,
            ActionRoundTimeoutProcessor timeoutProcessor,
            ActionRoundTimerRegistry timerRegistry) {
        this.taskScheduler = taskScheduler;
        this.timeoutProcessor = timeoutProcessor;
        this.timerRegistry = timerRegistry;
    }

    void scheduleAfterCommit(ActionRoundSaga.StartResult startResult) {
        scheduleAfterCommit(startResult.sagaId(), startResult.timerExpiresAt());
    }

    void reschedule(UUID sagaId, Instant timerExpiresAt) {
        // The callback needs to identify "am I still the current timer for this sagaId" so a fire
        // racing a concurrent replacement removes only itself — never the replacement. It can't
        // close over `future` directly (not yet assigned when the lambda is built), so the box
        // is set immediately after scheduling; timerExpiresAt is always in the future here, which
        // gives that assignment time to happen before the task could possibly run.
        var selfRef = new AtomicReference<ScheduledFuture<?>>();
        var future = taskScheduler.schedule(
                () -> {
                    timerRegistry.removeIfCurrent(sagaId, selfRef.get());
                    timeoutProcessor.handleTimerExpiry(sagaId);
                },
                timerExpiresAt);
        selfRef.set(future);
        timerRegistry.register(sagaId, future);
    }

    private void scheduleAfterCommit(UUID sagaId, Instant timerExpiresAt) {
        // Starting a timer before commit would let the callback race against a saga row that is not
        // durable yet. The afterCommit hook keeps timer visibility aligned with persistent state.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reschedule(sagaId, timerExpiresAt);
                }
            });
        } else {
            reschedule(sagaId, timerExpiresAt);
        }
    }
}
