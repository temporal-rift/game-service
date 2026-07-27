package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code cleanupInterval} is read by {@code @Scheduled} through the raw
 * {@code ${game.rate-limit.cleanup-interval}} placeholder, which bypasses this record — the yml key
 * is mandatory and a {@code @DefaultValue} would never apply. The field exists so startup
 * validation still bounds the configured value.
 */
@ConfigurationProperties("game.rate-limit")
@Validated
record RateLimitProperties(
        @Min(1) int requestsPerMinute,

        @NotNull @DurationMin(seconds = 1) Duration cleanupInterval) {}
