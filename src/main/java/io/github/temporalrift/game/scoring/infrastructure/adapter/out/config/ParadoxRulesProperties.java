package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.scoring.domain.port.out.ParadoxRulesPort;

/**
 * The paradox rules scoring reads from the namespace shared with timeline-service, which decides the cascade with the
 * same key.
 */
@ConfigurationProperties("game.rules.paradox")
@Validated
public record ParadoxRulesProperties(@DefaultValue("true") boolean detonateContestsStabilize)
        implements ParadoxRulesPort {}
