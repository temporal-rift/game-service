package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("game.rate-limit")
@Validated
record RateLimitProperties(
        @Min(1) int requestsPerMinute,

        @NotNull @DurationMin(nanos = 1) @DefaultValue("60s")
        Duration cleanupInterval) {}
