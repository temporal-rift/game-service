package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.scoring.domain.port.out.VictoryRulesPort;

@ConfigurationProperties("game.rules.victory")
@Validated
public record VictoryRulesProperties(
        @DefaultValue("4") @Min(1) int eraserAnnihilations,
        @DefaultValue("3") @Min(1) int prophetWrittenResolutions,
        @DefaultValue("3") @Min(1) int revisionistSuccessfulEras,
        // No built-in default: timeline-service completes the chain at this same shared value, so a missing key must
        // fail at startup rather than let the two services disagree.
        @Min(2) int weaverChainLength,
        @DefaultValue("3") @Min(1) int activistConsecutiveDeclarations,
        @DefaultValue("1") @Min(0) int finalEraObjectiveDiscount)
        implements VictoryRulesPort {}
