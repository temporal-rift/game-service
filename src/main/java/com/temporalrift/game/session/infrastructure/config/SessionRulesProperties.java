package com.temporalrift.game.session.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("game.rules")
@Validated
public record SessionRulesProperties(
        @Min(2) int minPlayers,
        @Min(2) int maxPlayers,
        @Min(1) int maxEras,
        @Min(1) int maxCascadedParadoxes,
        @Min(1) int eventsPerEra
) {}

