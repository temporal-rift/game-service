package io.github.temporalrift.game.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CardDrawWeightsTest {

    @Test
    @DisplayName("draws only ordinary card types at a grade each type supports")
    void drawsOrdinaryCardsAtSupportedGrades() {
        var weights = new CardDrawWeights(
                Map.of(
                        CardCategory.PROBABILITY_SHIFTER, 35,
                        CardCategory.INFORMATION, 25,
                        CardCategory.DISRUPTION, 25,
                        CardCategory.PARADOX, 15),
                Map.of(CardGrade.I, 60, CardGrade.II, 30, CardGrade.III, 10));
        var random = new Random(3);

        for (var i = 0; i < 500; i++) {
            var cardType = weights.drawType(random);
            var grade = weights.drawGrade(cardType, random);

            assertThat(cardType).isNotIn(CardType.STABILIZE, CardType.DETONATE);
            assertThat(cardType.supportedGrades()).contains(grade);
        }
    }

    @Test
    @DisplayName("a category with the only positive weight is always drawn")
    void onlyPositiveCategoryIsDrawn() {
        var weights = new CardDrawWeights(
                Map.of(CardCategory.PARADOX, 1, CardCategory.INFORMATION, 0), Map.of(CardGrade.I, 1));

        assertThat(weights.drawType(new Random(9))).isEqualTo(CardType.COLLIDE);
    }
}
