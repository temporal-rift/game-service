package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The interval fields are read by {@code @Scheduled} through raw {@code ${game.timers.*}}
 * placeholders, which bypass this record entirely — the yml keys are therefore mandatory, and a
 * {@code @DefaultValue} here would never apply to the schedules. The fields exist so startup
 * validation still bounds the configured values.
 */
@ConfigurationProperties("game.timers")
@Validated
record TimerProperties(
        @NotNull @DurationMin(nanos = 1) @DefaultValue("2m") Duration eventResubmitMinAge,

        @NotNull @DurationMin(seconds = 1) Duration eventResubmitInterval,

        @NotNull @DurationMin(seconds = 1) Duration actionRoundSweepInterval,

        @NotNull @DurationMin(seconds = 1) Duration reconnectSweepInterval,

        @NotNull @DurationMin(seconds = 1) Duration scoringCompletionSweepInterval,

        @NotNull @DurationMin(seconds = 1) Duration eraSagaScoresUpdatedSweepInterval) {}
