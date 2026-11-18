package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.session.domain.port.out.VictoryTiebreakRulesPort;

/** The session's part of the victory rules: how simultaneous winners are separated. */
@ConfigurationProperties("game.rules.victory")
@Validated
public record VictoryTiebreakRulesProperties(
        @DefaultValue("true") boolean scoreTiebreak) implements VictoryTiebreakRulesPort {}
