package io.github.temporalrift.game.session.application.dealing;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;
import io.github.temporalrift.game.shared.CardCategory;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.HandDealt;

/** Deals ordinary action cards by configured category and grade weights. */
@Component
public class WeightedCardDealer {

    private final SessionGameRulesPort gameRules;
    private final RandomGenerator random;

    public WeightedCardDealer(SessionGameRulesPort gameRules, RandomGenerator random) {
        this.gameRules = gameRules;
        this.random = random;
    }

    public List<HandDealt.CardInstance> deal(int cardCount) {
        return IntStream.range(0, cardCount).mapToObj(ignored -> dealCard()).toList();
    }

    private HandDealt.CardInstance dealCard() {
        var gradeWeights = gameRules.cardGradeWeights();
        var category = weightedChoice(gameRules.cardCategoryWeights(), eligibleCategories(gradeWeights));
        var eligibleTypes = eligibleTypes(category, gradeWeights);
        var cardType = eligibleTypes.get(random.nextInt(eligibleTypes.size()));
        var grade =
                weightedChoice(gradeWeights, cardType.supportedGrades().stream().toList());
        return new HandDealt.CardInstance(UUID.randomUUID(), cardType, grade);
    }

    private static List<CardCategory> eligibleCategories(Map<CardGrade, Integer> gradeWeights) {
        return Arrays.stream(CardCategory.values())
                .filter(category -> !eligibleTypes(category, gradeWeights).isEmpty())
                .toList();
    }

    private static List<CardType> eligibleTypes(CardCategory category, Map<CardGrade, Integer> gradeWeights) {
        return Arrays.stream(CardType.values())
                .filter(cardType -> cardType.getCategory() == category)
                .filter(cardType -> cardType != CardType.STABILIZE && cardType != CardType.DETONATE)
                .filter(cardType ->
                        cardType.supportedGrades().stream().anyMatch(grade -> gradeWeights.getOrDefault(grade, 0) > 0))
                .toList();
    }

    private <T> T weightedChoice(Map<T, Integer> weights, List<T> candidates) {
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
