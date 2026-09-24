package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@DisplayName("SpecialActionSubmission round rule")
class SpecialActionSubmissionRoundTest {

    @ParameterizedTest(name = "era {0} round {1}")
    @CsvSource({"1,1", "1,2", "2,1", "2,2", "3,1", "3,2", "4,1", "4,2", "5,1", "5,2"})
    @DisplayName("OBSCURE in rounds 1 and 2 is accepted in every era")
    void obscureAcceptedBeforeFinalRound(int eraNumber, int roundNumber) {
        assertThatCode(() -> obscure().validate(eraNumber, roundNumber)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "era {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("OBSCURE in round 3 is rejected in every era")
    void obscureRejectedInFinalRound(int eraNumber) {
        var obscure = obscure();

        assertThatExceptionOfType(SpecialActionNotEligibleForRoundException.class)
                .isThrownBy(() -> obscure.validate(eraNumber, 3))
                .withMessageContaining("OBSCURE");
    }

    private static SubmittedAction.SpecialActionSubmission obscure() {
        return new SubmittedAction.SpecialActionSubmission(
                UUID.randomUUID(), Faction.REVISIONISTS, SpecialAction.OBSCURE, null, null, null, null, null);
    }
}
