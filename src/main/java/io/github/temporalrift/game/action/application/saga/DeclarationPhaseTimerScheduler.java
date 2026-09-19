package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Schedules prompt expiry processing after an open declaration window is durable. */
@Component
class DeclarationPhaseTimerScheduler {

    private final TaskScheduler taskScheduler;
    private final DeclarationPhaseTimeoutProcessor timeoutProcessor;

    DeclarationPhaseTimerScheduler(
            @Qualifier("actionTaskScheduler") TaskScheduler taskScheduler,
            DeclarationPhaseTimeoutProcessor timeoutProcessor) {
        this.taskScheduler = taskScheduler;
        this.timeoutProcessor = timeoutProcessor;
    }

    void scheduleAfterCommit(UUID phaseId, Instant expiresAt) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(phaseId, expiresAt);
                }
            });
            return;
        }
        schedule(phaseId, expiresAt);
    }

    private void schedule(UUID phaseId, Instant expiresAt) {
        taskScheduler.schedule(() -> timeoutProcessor.resolve(phaseId), expiresAt);
    }
}
