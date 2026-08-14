package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Schedules prompt expiry processing after an open selection window is durable. */
@Component
class HandSelectionTimerScheduler {
    private final TaskScheduler taskScheduler;
    private final HandSelectionTimeoutProcessor timeoutProcessor;

    HandSelectionTimerScheduler(
            @Qualifier("actionTaskScheduler") TaskScheduler taskScheduler,
            HandSelectionTimeoutProcessor timeoutProcessor) {
        this.taskScheduler = taskScheduler;
        this.timeoutProcessor = timeoutProcessor;
    }

    void scheduleAfterCommit(UUID selectionId, Instant expiresAt) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(selectionId, expiresAt);
                }
            });
            return;
        }
        schedule(selectionId, expiresAt);
    }

    private void schedule(UUID selectionId, Instant expiresAt) {
        taskScheduler.schedule(() -> timeoutProcessor.resolve(selectionId), expiresAt);
    }
}
