package io.github.temporalrift.game.session.application.saga;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class PlayerReconnectTimerScheduler {

    private final TaskScheduler taskScheduler;
    private final PlayerReconnectTimeoutProcessor timeoutProcessor;
    private final PlayerReconnectTimerRegistry timerRegistry;

    PlayerReconnectTimerScheduler(
            TaskScheduler taskScheduler,
            PlayerReconnectTimeoutProcessor timeoutProcessor,
            PlayerReconnectTimerRegistry timerRegistry) {
        this.taskScheduler = taskScheduler;
        this.timeoutProcessor = timeoutProcessor;
        this.timerRegistry = timerRegistry;
    }

    void scheduleAfterCommit(PlayerReconnectSaga.StartResult startResult) {
        scheduleAfterCommit(startResult.sagaId(), startResult.graceExpiresAt());
    }

    void reschedule(UUID sagaId, Instant graceExpiresAt) {
        // The callback needs to identify "am I still the current timer for this sagaId" so a fire
        // racing a concurrent replacement removes only itself — never the replacement. It can't
        // close over `future` directly (not yet assigned when the lambda is built), so the box
        // is set immediately after scheduling; graceExpiresAt is always in the future here, which
        // gives that assignment time to happen before the task could possibly run.
        var selfRef = new AtomicReference<ScheduledFuture<?>>();
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> {
                    timerRegistry.removeIfCurrent(sagaId, selfRef.get());
                    timeoutProcessor.handleTimerExpiry(sagaId);
                },
                graceExpiresAt);
        selfRef.set(future);
        timerRegistry.register(sagaId, future);
    }

    private void scheduleAfterCommit(UUID sagaId, Instant graceExpiresAt) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reschedule(sagaId, graceExpiresAt);
                }
            });
        } else {
            reschedule(sagaId, graceExpiresAt);
        }
    }
}
