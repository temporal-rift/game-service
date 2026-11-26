package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.session.domain.port.out.SessionVictoryRulesPort;

/** The session's part of the victory rules: whether normal victory exists and how simultaneous winners separate. */
@ConfigurationProperties("game.rules.victory")
@Validated
public record SessionVictoryRulesProperties(
        @DefaultValue("true") boolean scoreTiebreak,
        @DefaultValue("true") boolean normalVictory) implements SessionVictoryRulesPort {}
