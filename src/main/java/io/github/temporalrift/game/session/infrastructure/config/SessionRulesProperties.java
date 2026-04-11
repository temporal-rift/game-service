package io.github.temporalrift.game.session.infrastructure.config;

import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("game.rules")
@Validated
public record SessionRulesProperties(
        @Min(2) int minPlayers,
        @Min(2) int maxPlayers,
        @Min(1) int maxEras,
        @Min(1) int maxCascadedParadoxes,
        @Min(1) int eventsPerEra,
        @NotEmpty Map<Integer, Integer> actionRoundTimerSeconds) {}
