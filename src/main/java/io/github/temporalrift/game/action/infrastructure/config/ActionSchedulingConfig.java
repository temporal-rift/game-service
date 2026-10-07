package io.github.temporalrift.game.action.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import io.github.temporalrift.game.shared.infrastructure.config.TimerTaskScheduler;

@Configuration
class ActionSchedulingConfig {

    @Bean("actionTaskScheduler")
    TaskScheduler actionTaskScheduler(@Value("${game.simulation.enabled:false}") boolean logicalTime) {
        var scheduler = new TimerTaskScheduler(logicalTime);
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("action-round-timer-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
