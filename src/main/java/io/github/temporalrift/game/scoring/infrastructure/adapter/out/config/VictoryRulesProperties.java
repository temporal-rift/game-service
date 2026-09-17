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
        @DefaultValue("5") @Min(1) int prophetWrittenResolutions,
        @DefaultValue("3") @Min(1) int revisionistSuccessfulEras,
        @DefaultValue("3") @Min(1) int weaverChainLength,
        @DefaultValue("3") @Min(1) int activistConsecutiveDeclarations)
        implements VictoryRulesPort {}
