package io.github.temporalrift.game;

import java.time.Clock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import io.github.temporalrift.game.shared.infrastructure.config.RateLimiterTestConfiguration;

@TestConfiguration
@Import(RateLimiterTestConfiguration.class)
public class TestSecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            throw new JwtException("Test decoder — inject auth via SecurityMockMvcRequestPostProcessors");
        };
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
