package io.github.temporalrift.game.action.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.shared.CardType;

class ScoringRulesPropertiesTest {

    static ScoringRulesProperties properties(int lowMaxProbability, int mediumMaxProbability) {
        return new ScoringRulesProperties(Map.of(CardType.PUSH, 20), 30, lowMaxProbability, mediumMaxProbability);
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
}
