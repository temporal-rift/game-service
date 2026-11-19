package io.github.temporalrift.game.scoring.infrastructure.adapter.out.config;

import java.util.List;
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
        // Table sizes at which the Activist's successful eras need not be consecutive.
        @DefaultValue List<Integer> activistNonConsecutivePlayerCounts)
        implements VictoryRulesPort {

    public VictoryRulesProperties {
        eraserAnnihilationsByPlayerCount = Map.copyOf(Objects.requireNonNull(eraserAnnihilationsByPlayerCount));
        eraserAnnihilationsByPlayerCount.forEach((playerCount, steps) -> {
            if (steps == null || steps < 1) {
                throw new IllegalArgumentException("game.rules.victory.eraser-annihilations-by-player-count["
                        + playerCount + "] must be at least 1");
            }
        });
        activistNonConsecutivePlayerCounts = List.copyOf(Objects.requireNonNull(activistNonConsecutivePlayerCounts));
    }

    @Override
    public int eraserAnnihilations(int playerCount) {
        return eraserAnnihilationsByPlayerCount.getOrDefault(playerCount, eraserAnnihilations);
    }

    @Override
    public boolean activistRequiresConsecutiveEras(int playerCount) {
        return !activistNonConsecutivePlayerCounts.contains(playerCount);
    }
}
