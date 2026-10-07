package io.github.temporalrift.game.session.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.temporalrift.game.shared.infrastructure.config.TimerTaskScheduler;

@Configuration
@EnableScheduling
class SchedulingConfig {

    @Bean
    TaskScheduler taskScheduler(@Value("${game.simulation.enabled:false}") boolean logicalTime) {
        var scheduler = new TimerTaskScheduler(logicalTime);
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("reconnect-timer-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
