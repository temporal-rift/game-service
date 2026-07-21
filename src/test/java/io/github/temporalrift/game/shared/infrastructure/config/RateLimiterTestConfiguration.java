package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class RateLimiterTestConfiguration {

    @Bean
    RateLimitProperties rateLimitProperties() {
        return new RateLimitProperties(60, Duration.ofMinutes(1));
    }

    @Bean
    PlayerRateLimiter playerRateLimiter(RateLimitProperties rateLimitProperties, Clock clock) {
        return new PlayerRateLimiter(rateLimitProperties, clock);
    }
}
