package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import java.util.Map;
import java.util.Objects;

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
        @DefaultValue("1") @Min(0) int finalEraObjectiveDiscount,
        // Table sizes whose Eraser objective differs from eraserAnnihilations, keyed by player count.
        @DefaultValue Map<Integer, Integer> eraserAnnihilationsByPlayerCount,
        // Whether the Activist's successful eras must be consecutive, for the table sizes that differ from the rule.
        @DefaultValue Map<Integer, Boolean> activistConsecutiveErasByPlayerCount,
        // Table sizes whose Prophet and Revisionist objectives differ from the values above, keyed by player count.
        @DefaultValue Map<Integer, Integer> prophetWrittenResolutionsByPlayerCount,
        @DefaultValue Map<Integer, Integer> revisionistSuccessfulErasByPlayerCount)
        implements VictoryRulesPort {

    public VictoryRulesProperties {
        prophetWrittenResolutionsByPlayerCount = requireSteps(
                "game.rules.victory.prophet-written-resolutions-by-player-count",
                prophetWrittenResolutionsByPlayerCount);
        revisionistSuccessfulErasByPlayerCount = requireSteps(
                "game.rules.victory.revisionist-successful-eras-by-player-count",
                revisionistSuccessfulErasByPlayerCount);
        eraserAnnihilationsByPlayerCount = Map.copyOf(Objects.requireNonNull(eraserAnnihilationsByPlayerCount));
        eraserAnnihilationsByPlayerCount.forEach((playerCount, steps) -> {
            if (steps == null || steps < 1) {
                throw new IllegalArgumentException("game.rules.victory.eraser-annihilations-by-player-count["
                        + playerCount + "] must be at least 1");
            }
        });
        activistConsecutiveErasByPlayerCount = Map.copyOf(Objects.requireNonNull(activistConsecutiveErasByPlayerCount));
    }

    @Override
    public int eraserAnnihilations(int playerCount) {
        return eraserAnnihilationsByPlayerCount.getOrDefault(playerCount, eraserAnnihilations);
    }

    @Override
    public int prophetWrittenResolutions(int playerCount) {
        return prophetWrittenResolutionsByPlayerCount.getOrDefault(playerCount, prophetWrittenResolutions);
    }

    @Override
    public int revisionistSuccessfulEras(int playerCount) {
        return revisionistSuccessfulErasByPlayerCount.getOrDefault(playerCount, revisionistSuccessfulEras);
    }

    private static Map<Integer, Integer> requireSteps(String property, Map<Integer, Integer> byPlayerCount) {
        Objects.requireNonNull(byPlayerCount);
        byPlayerCount.forEach((playerCount, steps) -> {
            if (steps == null || steps < 1) {
                throw new IllegalArgumentException(property + "[" + playerCount + "] must be at least 1");
            }
        });
        return Map.copyOf(byPlayerCount);
    }

    @Override
    public boolean activistRequiresConsecutiveEras(int playerCount) {
        return activistConsecutiveErasByPlayerCount.getOrDefault(playerCount, true);
    }
}
