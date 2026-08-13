package io.github.temporalrift.game.shared;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum CardType {
    PUSH(CardCategory.PROBABILITY_SHIFTER, CardGrade.I, CardGrade.II, CardGrade.III),
    SUPPRESS(CardCategory.PROBABILITY_SHIFTER, CardGrade.I, CardGrade.II, CardGrade.III),
    SWING(CardCategory.PROBABILITY_SHIFTER, CardGrade.I, CardGrade.II, CardGrade.III),
    AMPLIFY(CardCategory.PROBABILITY_SHIFTER, CardGrade.I, CardGrade.II, CardGrade.III),

    INTERCEPT(CardCategory.INFORMATION, CardGrade.I, CardGrade.II),
    SCAN(CardCategory.INFORMATION, CardGrade.I, CardGrade.II, CardGrade.III),
    TRACE(CardCategory.INFORMATION, CardGrade.I, CardGrade.II),
    DECOY(CardCategory.INFORMATION, CardGrade.I),

    JAM(CardCategory.DISRUPTION, CardGrade.I),
    STALL(CardCategory.DISRUPTION, CardGrade.I),
    REDIRECT(CardCategory.DISRUPTION, CardGrade.I),
    NULLIFY(CardCategory.DISRUPTION, CardGrade.I, CardGrade.II),

    COLLIDE(CardCategory.PARADOX, CardGrade.I),
    STABILIZE(CardCategory.PARADOX, CardGrade.I),
    DETONATE(CardCategory.PARADOX, CardGrade.I);

    private final CardCategory category;
    private final Set<CardGrade> supportedGrades;

    CardType(CardCategory category, CardGrade... supportedGrades) {
        this.category = category;
        this.supportedGrades = Set.copyOf(EnumSet.copyOf(Arrays.asList(supportedGrades)));
    }

    public static Set<CardType> byCategory(CardCategory category) {
        return Arrays.stream(values()).filter(c -> c.category == category).collect(Collectors.toUnmodifiableSet());
    }

    public CardCategory getCategory() {
        return category;
    }

    public Set<CardGrade> supportedGrades() {
        return supportedGrades;
    }
}
