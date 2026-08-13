package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.CardCategory;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.Faction;

@ConfigurationProperties("game.rules")
@Validated
public record SessionRulesProperties(
        @Min(2) int minPlayers,
        @Min(2) int maxPlayers,
        @Min(1) int maxEras,
        @Min(1) int maxCascadedParadoxes,
        @Min(1) int eventsPerEra,
        @Min(1) int cardsPerHand,
        @Min(1) int cardsPerDeal,
        @Min(1) int winScoreThreshold,
        @Min(1) int reconnectGracePeriodSeconds,
        @NotEmpty Map<Integer, Integer> actionRoundTimerSeconds,
        @NotEmpty Map<Integer, Integer> handSelectionTimerSeconds,
        @NotEmpty Map<CardCategory, Integer> cardCategoryWeights,
        @NotEmpty Map<CardGrade, Integer> cardGradeWeights,
        @NotEmpty Set<Faction> stabilizationWinnerFactions)
        implements SessionGameRulesPort {

    private static final int DEFAULT_ACTION_ROUND_TIMER_SECONDS = 60;

    public SessionRulesProperties {
        actionRoundTimerSeconds = Map.copyOf(actionRoundTimerSeconds);
        handSelectionTimerSeconds = Map.copyOf(handSelectionTimerSeconds);
        cardCategoryWeights = Map.copyOf(cardCategoryWeights);
        cardGradeWeights = Map.copyOf(cardGradeWeights);
        validateWeights(cardCategoryWeights, "card-category-weights");
        validateWeights(cardGradeWeights, "card-grade-weights");
    }

    @Override
    public int actionRoundTimerSeconds(int playerCount) {
        return actionRoundTimerSeconds.getOrDefault(playerCount, DEFAULT_ACTION_ROUND_TIMER_SECONDS);
    }

    @Override
    public int handSelectionTimerSeconds(int playerCount) {
        return handSelectionTimerSeconds.getOrDefault(playerCount, DEFAULT_ACTION_ROUND_TIMER_SECONDS);
    }

    private static void validateWeights(Map<?, Integer> weights, String propertyName) {
        if (weights.values().stream().anyMatch(weight -> weight == null || weight < 0)
                || weights.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            throw new IllegalArgumentException(
                    propertyName + " must contain non-negative weights with a positive total");
        }
    }
}
