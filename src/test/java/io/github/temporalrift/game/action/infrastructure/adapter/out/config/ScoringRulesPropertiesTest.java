package io.github.temporalrift.game.action.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.shared.CardType;

class ScoringRulesPropertiesTest {

    static Map<CardType, Integer> exhaustiveCardShifts() {
        var shifts = new HashMap<CardType, Integer>();
        Arrays.stream(CardType.values())
                .filter(cardType -> cardType != CardType.SWING)
                .forEach(cardType -> shifts.put(cardType, 0));
        shifts.put(CardType.PUSH, 20);
        shifts.put(CardType.SUPPRESS, -20);
        return shifts;
    }

    static ScoringRulesProperties properties(int lowMaxProbability, int mediumMaxProbability) {
        return new ScoringRulesProperties(exhaustiveCardShifts(), 30, lowMaxProbability, mediumMaxProbability);
    }

    @Test
    @DisplayName("lowMaxProbability < mediumMaxProbability constructs successfully")
    void validOrdering_constructs() {
        assertThat(properties(30, 60).lowMaxProbability()).isEqualTo(30);
    }

    @Test
    @DisplayName("lowMaxProbability == mediumMaxProbability constructs successfully")
    void equalThresholds_constructs() {
        assertThat(properties(50, 50).mediumMaxProbability()).isEqualTo(50);
    }

    @Test
    @DisplayName("lowMaxProbability > mediumMaxProbability throws IllegalArgumentException")
    void invertedOrdering_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(60, 30));
    }

    @Test
    @DisplayName("card-shifts missing an entry for a non-SWING card type throws IllegalArgumentException")
    void missingCardTypeEntry_throws() {
        var shifts = exhaustiveCardShifts();
        shifts.remove(CardType.JAM);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoringRulesProperties(shifts, 30, 30, 60))
                .withMessageContaining("JAM");
    }

    @Test
    @DisplayName("card-shifts is not required to contain SWING — it is configured via swingShift instead")
    void missingSwingEntry_constructsSuccessfully() {
        var shifts = exhaustiveCardShifts();
        shifts.remove(CardType.SWING);

        assertThat(new ScoringRulesProperties(shifts, 30, 30, 60).cardShifts()).doesNotContainKey(CardType.SWING);
    }
}
