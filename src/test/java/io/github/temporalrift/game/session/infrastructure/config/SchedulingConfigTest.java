package io.github.temporalrift.game.session.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.infrastructure.config.TimerTaskScheduler;

class SchedulingConfigTest {

    @Test
    @DisplayName("the session timer scheduler is a timer scheduler named for reconnect timers")
    void taskScheduler_isATimerScheduler() {
        var scheduler = new SchedulingConfig().taskScheduler(false);

        assertThat(scheduler).isInstanceOf(TimerTaskScheduler.class);
        assertThat(((TimerTaskScheduler) scheduler).getThreadNamePrefix()).isEqualTo("reconnect-timer-");
    }
}
