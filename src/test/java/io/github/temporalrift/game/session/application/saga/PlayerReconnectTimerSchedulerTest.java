package io.github.temporalrift.game.session.application.saga;

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
class PlayerReconnectTimerSchedulerTest {

    @Mock
    TaskScheduler taskScheduler;

    @Mock
    PlayerReconnectTimeoutProcessor timeoutProcessor;

    @Mock
    PlayerReconnectTimerRegistry timerRegistry;

    @Mock
    ScheduledFuture<Object> future;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("reschedule registers scheduled future and invokes timeout callback when fired")
    void reschedule_registersFutureAndInvokesTimeout() {
        // given
        var scheduler = new PlayerReconnectTimerScheduler(taskScheduler, timeoutProcessor, timerRegistry);
        var sagaId = UUID.randomUUID();
        var expiresAt = Instant.parse("2099-01-01T00:00:30Z");
        var runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(taskScheduler).schedule(runnableCaptor.capture(), eq(expiresAt));

        // when
        scheduler.reschedule(sagaId, expiresAt);
        runnableCaptor.getValue().run();

        // then
        then(timerRegistry).should().register(sagaId, future);
        then(timerRegistry).should().remove(sagaId);
        then(timeoutProcessor).should().handleTimerExpiry(sagaId);
    }

    @Test
    @DisplayName("scheduleAfterCommit delays scheduling until transaction commit")
    void scheduleAfterCommit_withSynchronization_defersScheduling() {
        // given
        var scheduler = new PlayerReconnectTimerScheduler(taskScheduler, timeoutProcessor, timerRegistry);
        var result = new PlayerReconnectSaga.StartResult(UUID.randomUUID(), Instant.parse("2099-01-01T00:00:20Z"));
        TransactionSynchronizationManager.initSynchronization();

        // when
        scheduler.scheduleAfterCommit(result);

        // then
        then(taskScheduler).should(never()).schedule(any(Runnable.class), any(Instant.class));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        then(taskScheduler).should().schedule(any(Runnable.class), eq(result.graceExpiresAt()));
    }

    @Test
    @DisplayName("scheduleAfterCommit schedules immediately when no transaction synchronization is active")
    void scheduleAfterCommit_withoutSynchronization_schedulesImmediately() {
        // given
        var scheduler = new PlayerReconnectTimerScheduler(taskScheduler, timeoutProcessor, timerRegistry);
        var result = new PlayerReconnectSaga.StartResult(UUID.randomUUID(), Instant.parse("2099-01-01T00:00:20Z"));

        // when
        scheduler.scheduleAfterCommit(result);

        // then
        then(taskScheduler).should().schedule(any(Runnable.class), eq(result.graceExpiresAt()));
    }
}
