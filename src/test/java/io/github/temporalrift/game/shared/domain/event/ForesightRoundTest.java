package io.github.temporalrift.game.shared.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Foresight facts carry a real write round")
class ForesightRoundTest {

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void foresightDeclared_refusesARoundBelowOne(int roundNumber) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ForesightDeclared(
                        UUID.randomUUID(), 1, roundNumber, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .withMessageContaining("roundNumber");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void foresightFact_refusesARoundBelowOne(int roundNumber) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EraActionFactsFinalized.ForesightFact(
                        roundNumber, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .withMessageContaining("roundNumber");
    }

    @Test
    void acceptsTheFirstRound() {
        assertThat(new EraActionFactsFinalized.ForesightFact(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                        .roundNumber())
                .isEqualTo(1);
    }
}
