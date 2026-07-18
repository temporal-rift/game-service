package io.github.temporalrift.game.shared;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum CardType {
    PUSH(CardCategory.PROBABILITY_SHIFTER),
    SUPPRESS(CardCategory.PROBABILITY_SHIFTER),
    SWING(CardCategory.PROBABILITY_SHIFTER),
    AMPLIFY(CardCategory.PROBABILITY_SHIFTER),

    INTERCEPT(CardCategory.INFORMATION),
    SCAN(CardCategory.INFORMATION),
    TRACE(CardCategory.INFORMATION),
    DECOY(CardCategory.INFORMATION),

    JAM(CardCategory.DISRUPTION),
    STALL(CardCategory.DISRUPTION),
    REDIRECT(CardCategory.DISRUPTION),
    NULLIFY(CardCategory.DISRUPTION),

    COLLIDE(CardCategory.PARADOX),
    STABILIZE(CardCategory.PARADOX),
    DETONATE(CardCategory.PARADOX);

    private final CardCategory category;

    CardType(CardCategory category) {
        this.category = category;
    }

    public static Set<CardType> byCategory(CardCategory category) {
        return Arrays.stream(values()).filter(c -> c.category == category).collect(Collectors.toUnmodifiableSet());
    }

    public CardCategory getCategory() {
        return category;
    }
}
