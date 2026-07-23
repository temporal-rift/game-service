package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OperationalTimerPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void timerProperties_rejectSubSecondRecurringIntervals() {
        contextRunner
                .withPropertyValues(
                        "game.timers.event-resubmit-min-age=2m",
                        "game.timers.event-resubmit-interval=999ms",
                        "game.timers.action-round-sweep-interval=1s",
                        "game.timers.reconnect-sweep-interval=1s",
                        "game.rate-limit.requests-per-minute=120",
                        "game.rate-limit.cleanup-interval=60s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rateLimitProperties_rejectSubSecondCleanupInterval() {
        contextRunner
                .withPropertyValues(
                        "game.timers.event-resubmit-min-age=2m",
                        "game.timers.event-resubmit-interval=30s",
                        "game.timers.action-round-sweep-interval=1s",
                        "game.timers.reconnect-sweep-interval=1s",
                        "game.rate-limit.requests-per-minute=120",
                        "game.rate-limit.cleanup-interval=999ms")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({TimerProperties.class, RateLimitProperties.class})
    static class PropertiesConfiguration {}
}
