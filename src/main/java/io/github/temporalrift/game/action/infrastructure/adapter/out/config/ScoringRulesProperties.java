package io.github.temporalrift.game.action.infrastructure.adapter.out.config;

import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.game.action.domain.port.out.BandRulesPort;

@ConfigurationProperties("game.rules.scoring")
@Validated
public record ScoringRulesProperties(
        @NotNull Map<CardType, Integer> cardShifts,
        @Min(0) int swingShift,
        @Min(0) int lowMaxProbability,
        @Min(0) int mediumMaxProbability)
        implements BandRulesPort {

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
