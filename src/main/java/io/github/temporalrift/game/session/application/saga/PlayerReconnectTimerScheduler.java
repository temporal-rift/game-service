package io.github.temporalrift.game.session.application.saga;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

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
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> {
                    timerRegistry.remove(sagaId);
                    timeoutProcessor.handleTimerExpiry(sagaId);
                },
                graceExpiresAt);
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
