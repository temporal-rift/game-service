package io.github.temporalrift.game.session.domain.ending;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NormalVictoryPolicyTest {

    record Qualifier(String name, int total) {}

    @Test
    @DisplayName("with the tiebreak on, the higher-scoring qualifier wins alone")
    void higherScoringQualifierWins() {
        var first = new Qualifier("first", 14);
        var second = new Qualifier("second", 11);

        assertThat(NormalVictoryPolicy.winners(List.of(first, second), Qualifier::total, true))
                .containsExactly(first);
    }

    @Test
    @DisplayName("qualifiers tied on the highest total share")
    void tiedQualifiersShare() {
        var first = new Qualifier("first", 14);
        var second = new Qualifier("second", 14);
        var third = new Qualifier("third", 9);

        assertThat(NormalVictoryPolicy.winners(List.of(first, second, third), Qualifier::total, true))
                .containsExactly(first, second);
    }

    @Test
    @DisplayName("with the tiebreak off, every qualifier wins")
    void tiebreakOffKeepsEveryQualifier() {
        var first = new Qualifier("first", 14);
        var second = new Qualifier("second", 11);

        assertThat(NormalVictoryPolicy.winners(List.of(first, second), Qualifier::total, false))
                .containsExactly(first, second);
    }
}
