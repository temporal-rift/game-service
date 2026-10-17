package io.github.temporalrift.game.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class CardTypeTest {

    @Test
    void supportedGradesIterateInGradeOrderSoSeededDrawsDoNotDependOnTheJvm() {
        for (var cardType : CardType.values()) {
            var expected = Arrays.stream(CardGrade.values())
                    .filter(cardType.supportedGrades()::contains)
                    .toList();

            assertThat(cardType.supportedGrades()).containsExactlyElementsOf(expected);
        }
    }
}
