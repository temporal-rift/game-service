package io.github.temporalrift.game.shared.infrastructure.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("game.timers")
@Validated
record TimerProperties(
        @NotNull @DefaultValue("2m") Duration eventResubmitMinAge,
        @NotNull @DefaultValue("30s") Duration eventResubmitInterval) {}
