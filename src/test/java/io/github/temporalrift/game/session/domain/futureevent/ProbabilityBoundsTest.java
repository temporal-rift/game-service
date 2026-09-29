package io.github.temporalrift.game.session.domain.futureevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProbabilityBoundsTest {

    @ParameterizedTest
    @CsvSource({"-1, 90", "0, 101", "50, 50", "60, 40"})
    void invalidBounds_areRejected(int floor, int ceiling) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProbabilityBounds(floor, ceiling));
    }

    @Test
    void contains_isInclusiveAtBothEnds() {
        var bounds = new ProbabilityBounds(0, 90);

        assertThat(bounds.contains(0)).isTrue();
        assertThat(bounds.contains(90)).isTrue();
        assertThat(bounds.contains(91)).isFalse();
    }
}
