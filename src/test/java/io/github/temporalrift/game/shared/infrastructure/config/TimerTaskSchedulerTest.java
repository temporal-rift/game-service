package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimerTaskSchedulerTest {

    @Test
    @DisplayName("with logical time a one-shot timer never fires on the wall clock and can still be cancelled")
    void schedule_withLogicalTime_neverFiresAndCancels() throws InterruptedException {
        var scheduler = new TimerTaskScheduler(true);
        scheduler.initialize();
        try {
            var fired = new AtomicBoolean();
            var armed = new CountDownLatch(1);

            var timer = scheduler.schedule(
                    () -> {
                        fired.set(true);
                        armed.countDown();
                    },
                    Instant.now());
            var firedEarly = fired.get() || armed.await(200, TimeUnit.MILLISECONDS);

            assertThat(firedEarly).isFalse();
            assertThat(timer.isDone()).isFalse();
            assertThat(timer.cancel(false)).isTrue();
            assertThat(timer.isCancelled()).isTrue();
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    @DisplayName("with wall-clock time a one-shot timer fires at its deadline")
    void schedule_withWallClockTime_fires() throws InterruptedException {
        var scheduler = new TimerTaskScheduler(false);
        scheduler.initialize();
        try {
            var fired = new CountDownLatch(1);

            scheduler.schedule(fired::countDown, Instant.now());

            assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            scheduler.shutdown();
        }
    }
}
