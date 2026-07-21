package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("game.timers")
@Validated
record TimerProperties(
        @NotNull @DurationMin(nanos = 1) @DefaultValue("2m") Duration eventResubmitMinAge,

        @NotNull @DurationMin(nanos = 1) @DefaultValue("30s")
        Duration eventResubmitInterval) {}
