package io.github.temporalrift.game.shared.domain.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Configured category and grade weights for drawing an ordinary action card. */
public record CardDrawWeights(Map<CardCategory, Integer> categoryWeights, Map<CardGrade, Integer> gradeWeights) {

    public CardDrawWeights {
        categoryWeights = Map.copyOf(categoryWeights);
        gradeWeights = Map.copyOf(gradeWeights);
    }

    /** Draws a weighted category, then a uniform card type of that category playable at an enabled grade. */
    public CardType drawType(RandomGenerator random) {
        var category = weightedChoice(categoryWeights, eligibleCategories(), random);
        var eligibleTypes = eligibleTypes(category);
        return eligibleTypes.get(random.nextInt(eligibleTypes.size()));
    }

    public CardGrade drawGrade(CardType cardType, RandomGenerator random) {
        return weightedChoice(gradeWeights, cardType.supportedGrades().stream().toList(), random);
    }

    private List<CardCategory> eligibleCategories() {
        return Arrays.stream(CardCategory.values())
                .filter(category -> !eligibleTypes(category).isEmpty())
                .toList();
    }

    private List<CardType> eligibleTypes(CardCategory category) {
        return Arrays.stream(CardType.values())
                .filter(cardType -> cardType.getCategory() == category)
                .filter(cardType -> cardType != CardType.STABILIZE && cardType != CardType.DETONATE)
                .filter(cardType ->
                        cardType.supportedGrades().stream().anyMatch(grade -> gradeWeights.getOrDefault(grade, 0) > 0))
                .toList();
    }

    private static <T> T weightedChoice(Map<T, Integer> weights, List<T> candidates, RandomGenerator random) {
        var total = candidates.stream()
                .mapToInt(candidate -> weights.getOrDefault(candidate, 0))
                .sum();
        if (total <= 0) {
            throw new IllegalStateException("No positive weight is configured for eligible card choices");
        }
        var roll = random.nextInt(total);
        for (var candidate : candidates) {
            roll -= weights.getOrDefault(candidate, 0);
            if (roll < 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("Weighted choice did not resolve a candidate");
    }
}
