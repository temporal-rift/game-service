package io.github.temporalrift.game.action.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.CardGrade;

class ScoringRulesPropertiesTest {

    static Map<CardGrade, Integer> pushShifts() {
        return Map.of(CardGrade.I, 10, CardGrade.II, 20, CardGrade.III, 30);
    }

    static Map<CardGrade, Integer> suppressShifts() {
        return Map.of(CardGrade.I, -10, CardGrade.II, -20, CardGrade.III, -30);
    }

    static Map<CardGrade, Integer> swingShifts() {
        return Map.of(CardGrade.I, 15, CardGrade.II, 30, CardGrade.III, 45);
    }

    static ScoringRulesProperties properties(int bandLowMax, int bandMediumMax) {
        return new ScoringRulesProperties(pushShifts(), suppressShifts(), swingShifts(), bandLowMax, bandMediumMax);
    }

    @Test
    @DisplayName("bandLowMax < bandMediumMax constructs successfully")
    void validOrdering_constructs() {
        assertThat(properties(30, 60).bandLowMax()).isEqualTo(30);
    }

    @Test
    @DisplayName("bandLowMax == bandMediumMax constructs successfully")
    void equalThresholds_constructs() {
        assertThat(properties(50, 50).bandMediumMax()).isEqualTo(50);
    }

    @Test
    @DisplayName("bandLowMax > bandMediumMax throws IllegalArgumentException")
    void invertedOrdering_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(60, 30));
    }

    @Test
    @DisplayName("push-shift missing an entry for a grade throws IllegalArgumentException")
    void missingPushShiftGrade_throws() {
        var shifts = Map.of(CardGrade.I, 10, CardGrade.II, 20);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoringRulesProperties(shifts, suppressShifts(), swingShifts(), 30, 60))
                .withMessageContaining("III");
    }

    @Test
    @DisplayName("suppress-shift missing an entry for a grade throws IllegalArgumentException")
    void missingSuppressShiftGrade_throws() {
        var shifts = Map.of(CardGrade.II, -20, CardGrade.III, -30);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoringRulesProperties(pushShifts(), shifts, swingShifts(), 30, 60))
                .withMessageContaining("I");
    }

    @Test
    @DisplayName("swing-shift missing an entry for a grade throws IllegalArgumentException")
    void missingSwingShiftGrade_throws() {
        var shifts = Map.of(CardGrade.I, 15, CardGrade.III, 45);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoringRulesProperties(pushShifts(), suppressShifts(), shifts, 30, 60))
                .withMessageContaining("II");
    }

    @Test
    @DisplayName("pushShift/suppressShift/swingShift look up the configured grade")
    void shiftLookups_returnConfiguredValues() {
        var props = properties(30, 60);

        assertThat(props.pushShift(CardGrade.III)).isEqualTo(30);
        assertThat(props.suppressShift(CardGrade.I)).isEqualTo(-10);
        assertThat(props.swingShift(CardGrade.II)).isEqualTo(30);
    }

    @Test
    @DisplayName("bandLowMaxProbability/bandMediumMaxProbability expose the configured band thresholds")
    void bandThresholds_exposedViaPort() {
        var props = properties(30, 60);

        assertThat(props.bandLowMaxProbability()).isEqualTo(30);
        assertThat(props.bandMediumMaxProbability()).isEqualTo(60);
    }
}
