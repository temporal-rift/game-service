package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import io.github.temporalrift.game.session.domain.ending.StabilizationThresholds;
import io.github.temporalrift.game.session.domain.futureevent.ProbabilityBounds;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@ConfigurationProperties("game.rules")
@Validated
public record SessionRulesProperties(
        @Min(2) int minPlayers,
        @Min(2) int maxPlayers,
        @Min(1) int maxEras,
        @Min(1) int maxCascadedParadoxes,
        @NotEmpty Map<Integer, Integer> eventsPerEra,
        @Min(1) int cardsPerHand,
        @Min(1) int cardsPerDeal,
        @Min(1) int winScoreThreshold,
        @Min(1) int reconnectGracePeriodSeconds,
        @NotEmpty Map<Integer, Integer> actionRoundTimerSeconds,
        @Min(1) int handSelectionTimerSeconds,
        Map<Integer, Integer> declarationTimerSeconds,
        @NotEmpty Map<CardCategory, Integer> cardCategoryWeights,
        @NotEmpty Map<CardGrade, Integer> cardGradeWeights,
        @DefaultValue @Valid Stabilization stabilization,
        @NotEmpty Set<SpecialAction> onceEraBudgetedSpecials,
        @DefaultValue("2") @Min(1) int sealMaxUsesPerGame,
        @DefaultValue("2") @Min(1) @Max(3) int annihilateEarliestRound,
        @DefaultValue("2") @Min(1) @Max(3) int rewriteLatestRound,
        Set<CardType> handDealForcedTypes,
        @NotNull ProbabilityBounds probability)
        implements SessionGameRulesPort {

    private static final int DEFAULT_ACTION_ROUND_TIMER_SECONDS = 180;
    private static final int DEFAULT_DECLARATION_TIMER_SECONDS = 120;

    /**
     * {@code factionClaims} defaults to {@code false}: stabilization goes to the highest score unless the Prophet and
     * Weaver claims are switched back on, in which case the two thresholds apply.
     */
    public record Stabilization(
            @DefaultValue("3") @Min(1) int prophetWrittenResolutions,
            @DefaultValue("2") @Min(1) int weaverActiveChainLinks,
            @DefaultValue("false") boolean factionClaims) {}

    public SessionRulesProperties {
        if (cardsPerDeal < cardsPerHand) {
            throw new IllegalArgumentException("cards-per-deal must be greater than or equal to cards-per-hand");
        }
        eventsPerEra = Map.copyOf(Objects.requireNonNull(eventsPerEra, "game.rules.events-per-era is required"));
        validatePositiveValues(eventsPerEra, "events-per-era");
        for (int playerCount = minPlayers; playerCount <= maxPlayers; playerCount++) {
            if (!eventsPerEra.containsKey(playerCount)) {
                throw new IllegalArgumentException(
                        "game.rules.events-per-era has no entry for " + playerCount + " players");
            }
        }
        actionRoundTimerSeconds = Map.copyOf(
                Objects.requireNonNull(actionRoundTimerSeconds, "game.rules.action-round-timer-seconds is required"));
        declarationTimerSeconds = declarationTimerSeconds == null ? Map.of() : Map.copyOf(declarationTimerSeconds);
        cardCategoryWeights =
                Map.copyOf(Objects.requireNonNull(cardCategoryWeights, "game.rules.card-category-weights is required"));
        cardGradeWeights =
                Map.copyOf(Objects.requireNonNull(cardGradeWeights, "game.rules.card-grade-weights is required"));
        handDealForcedTypes = handDealForcedTypes == null ? Set.of() : Set.copyOf(handDealForcedTypes);
        if (handDealForcedTypes.size() > cardsPerDeal) {
            throw new IllegalArgumentException("hand-deal-forced-types must not exceed cards-per-deal");
        }
        validatePositiveValues(actionRoundTimerSeconds, "action-round-timer-seconds");
        validatePositiveValues(declarationTimerSeconds, "declaration-timer-seconds");
        validateWeights(cardCategoryWeights, "card-category-weights");
        validateWeights(cardGradeWeights, "card-grade-weights");
    }

    @Override
    public StabilizationThresholds stabilizationThresholds() {
        return new StabilizationThresholds(
                stabilization.prophetWrittenResolutions(),
                stabilization.weaverActiveChainLinks(),
                stabilization.factionClaims());
    }

    @Override
    public int eventsPerEra(int playerCount) {
        var count = eventsPerEra.get(playerCount);
        if (count == null) {
            throw new IllegalArgumentException(
                    "game.rules.events-per-era has no entry for " + playerCount + " players");
        }
        return count;
    }

    @Override
    public ProbabilityBounds probabilityBounds() {
        return probability;
    }

    @Override
    public int actionRoundTimerSeconds(int playerCount) {
        return actionRoundTimerSeconds.getOrDefault(playerCount, DEFAULT_ACTION_ROUND_TIMER_SECONDS);
    }

    @Override
    public int declarationTimerSeconds(int playerCount) {
        return declarationTimerSeconds.getOrDefault(playerCount, DEFAULT_DECLARATION_TIMER_SECONDS);
    }

    private static void validateWeights(Map<?, Integer> weights, String propertyName) {
        if (weights.values().stream().anyMatch(weight -> weight == null || weight < 0)
                || weights.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            throw new IllegalArgumentException(
                    propertyName + " must contain non-negative weights with a positive total");
        }
    }

    private static void validatePositiveValues(Map<?, Integer> values, String propertyName) {
        if (values.values().stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(propertyName + " must contain only positive values");
        }
    }
}
