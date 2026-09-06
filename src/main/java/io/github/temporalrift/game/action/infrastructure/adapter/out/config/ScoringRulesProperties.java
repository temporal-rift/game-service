package io.github.temporalrift.game.action.infrastructure.adapter.out.config;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.action.domain.port.out.BandRulesPort;
import io.github.temporalrift.game.shared.CardGrade;

@ConfigurationProperties("game.rules.probability")
@Validated
public record ScoringRulesProperties(
        @NotNull Map<CardGrade, Integer> pushShift,
        @NotNull Map<CardGrade, Integer> suppressShift,
        @NotNull Map<CardGrade, Integer> swingShift,
        @Min(0) int bandLowMax,
        @Min(0) int bandMediumMax)
        implements BandRulesPort {

    public ScoringRulesProperties {
        requireAllGrades(pushShift, "push-shift");
        requireAllGrades(suppressShift, "suppress-shift");
        requireAllGrades(swingShift, "swing-shift");
        if (bandLowMax > bandMediumMax) {
            throw new IllegalArgumentException("game.rules.probability.band-low-max must be <= band-medium-max");
        }
    }

    private static void requireAllGrades(Map<CardGrade, Integer> values, String property) {
        Objects.requireNonNull(values, "game.rules.probability." + property + " must not be null");
        var missing = Arrays.stream(CardGrade.values())
                .filter(grade -> !values.containsKey(grade))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "game.rules.probability." + property + " is missing entries for: " + missing);
        }
    }

    // requireAllGrades guarantees every CardGrade key is present, so these lookups never return null.

    @Override
    public int pushShift(CardGrade grade) {
        return pushShift.get(grade);
    }

    @Override
    public int suppressShift(CardGrade grade) {
        return suppressShift.get(grade);
    }

    @Override
    public int swingShift(CardGrade grade) {
        return swingShift.get(grade);
    }

    @Override
    public int bandLowMaxProbability() {
        return bandLowMax;
    }

    @Override
    public int bandMediumMaxProbability() {
        return bandMediumMax;
    }
}
