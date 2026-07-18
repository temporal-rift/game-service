package io.github.temporalrift.game.action.infrastructure.adapter.out.config;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.action.domain.port.out.BandRulesPort;

@ConfigurationProperties("game.rules.scoring")
@Validated
public record ScoringRulesProperties(
        @NotNull Map<CardType, Integer> cardShifts,
        @Min(0) int swingShift,
        @Min(0) int lowMaxProbability,
        @Min(0) int mediumMaxProbability)
        implements BandRulesPort {

    public ScoringRulesProperties {
        Objects.requireNonNull(cardShifts, "game.rules.scoring.card-shifts must not be null");
        if (lowMaxProbability > mediumMaxProbability) {
            throw new IllegalArgumentException(
                    "game.rules.scoring.low-max-probability must be <= medium-max-probability");
        }
        // SWING is configured separately via swingShift; every other card type must have an explicit
        // entry (0 for non-shifters) so a newly added CardType with no config entry fails fast at
        // startup instead of silently defaulting to a 0 shift in cardShift(CardType).
        var missing = Arrays.stream(CardType.values())
                .filter(cardType -> cardType != CardType.SWING && !cardShifts.containsKey(cardType))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("game.rules.scoring.card-shifts is missing entries for: " + missing);
        }
    }

    @Override
    public int cardShift(CardType cardType) {
        return cardShifts.getOrDefault(cardType, 0);
    }

    @Override
    public int bandLowMaxProbability() {
        return lowMaxProbability;
    }

    @Override
    public int bandMediumMaxProbability() {
        return mediumMaxProbability;
    }
}
