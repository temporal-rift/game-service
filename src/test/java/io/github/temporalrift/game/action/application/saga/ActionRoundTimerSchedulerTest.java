package io.github.temporalrift.game.action.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ActionRoundTimerSchedulerTest {

    @Mock
    TaskScheduler taskScheduler;

    @Mock
    ActionRoundTimeoutProcessor timeoutProcessor;

    @Mock
    ActionRoundTimerRegistry timerRegistry;

    @Mock
    ScheduledFuture<Object> scheduledFuture;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("reschedule schedules timeout callback and invokes the processor when fired")
    void reschedule_schedulesCallbackThatInvokesTimeoutProcessor() {
        // given
        var scheduler = new ActionRoundTimerScheduler(taskScheduler, timeoutProcessor, timerRegistry);
        var sagaId = UUID.randomUUID();
        var expiresAt = Instant.parse("2099-01-01T00:00:10Z");
        var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(scheduledFuture).when(taskScheduler).schedule(runnableCaptor.capture(), eq(expiresAt));

        // when
        scheduler.reschedule(sagaId, expiresAt);
        runnableCaptor.getValue().run();

        // then
        then(taskScheduler).should().schedule(any(Runnable.class), eq(expiresAt));
        then(timeoutProcessor).should().handleTimerExpiry(sagaId);
    }

    @Test
    @DisplayName("scheduleAfterCommit delays scheduling until after transaction commit")
    void scheduleAfterCommit_withSynchronization_registersAfterCommitHook() {
        // given
        var scheduler = new ActionRoundTimerScheduler(taskScheduler, timeoutProcessor, timerRegistry);
        var result = new ActionRoundSaga.StartResult(UUID.randomUUID(), Instant.parse("2099-01-01T00:00:15Z"));
        TransactionSynchronizationManager.initSynchronization();
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), eq(result.timerExpiresAt()));

        // when
        scheduler.scheduleAfterCommit(result);

        // then
        then(taskScheduler).should(never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        then(taskScheduler).should().schedule(any(Runnable.class), eq(result.timerExpiresAt()));
    }

    @Test
    @DisplayName("scheduleAfterCommit schedules immediately when no transaction synchronization is active")
    void scheduleAfterCommit_withoutSynchronization_schedulesImmediately() {
        // given
        var scheduler = new ActionRoundTimerScheduler(taskScheduler, timeoutProcessor, timerRegistry);
        var result = new ActionRoundSaga.StartResult(UUID.randomUUID(), Instant.parse("2099-01-01T00:00:15Z"));
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), eq(result.timerExpiresAt()));

        // when
        scheduler.scheduleAfterCommit(result);

        // then
        then(taskScheduler).should().schedule(any(Runnable.class), eq(result.timerExpiresAt()));
    }
}
