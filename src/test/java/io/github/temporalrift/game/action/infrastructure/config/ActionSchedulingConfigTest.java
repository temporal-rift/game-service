package io.github.temporalrift.game.action.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.infrastructure.config.TimerTaskScheduler;

class ActionSchedulingConfigTest {

    @Test
    @DisplayName("the action timer scheduler is a timer scheduler named for action rounds")
    void actionTaskScheduler_isATimerScheduler() {
        var scheduler = new ActionSchedulingConfig().actionTaskScheduler(true);

        assertThat(scheduler).isInstanceOf(TimerTaskScheduler.class);
        assertThat(((TimerTaskScheduler) scheduler).getThreadNamePrefix()).isEqualTo("action-round-timer-");
    }
}
