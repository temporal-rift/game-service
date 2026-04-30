package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionRulesPropertiesTest {

    static SessionRulesProperties properties(Map<Integer, Integer> timers) {
        return new SessionRulesProperties(2, 8, 4, 3, 5, 7, 100, timers);
    }

    @Test
    @DisplayName("actionRoundTimerSeconds returns the mapped value for a known player count")
    void actionRoundTimerSeconds_knownCount_returnsMappedValue() {
        // given
        var props = properties(Map.of(4, 45, 6, 90));

        // when / then
        assertThat(props.actionRoundTimerSeconds(4)).isEqualTo(45);
        assertThat(props.actionRoundTimerSeconds(6)).isEqualTo(90);
    }

    @Test
    @DisplayName("actionRoundTimerSeconds returns 60 for an unmapped player count")
    void actionRoundTimerSeconds_unknownCount_returnsDefault() {
        // given
        var props = properties(Map.of(4, 45));

        // when / then
        assertThat(props.actionRoundTimerSeconds(7)).isEqualTo(60);
    }
}
