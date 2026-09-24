package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

@DisplayName("SpecialActionSubmission final-era rule")
class SpecialActionSubmissionFinalEraTest {

    private static final int MAX_ERAS = 5;

    @Test
    @DisplayName("CASCADE in the final era is rejected")
    void finalEraCascadeRejected() {
        var cascade = submission(Faction.ERASERS, SpecialAction.CASCADE);

        assertThatExceptionOfType(SpecialActionNotEligibleForEraException.class)
                .isThrownBy(() -> cascade.validate(MAX_ERAS, 1, MAX_ERAS));
    }

    @Test
    @DisplayName("CASCADE before the final era is accepted")
    void earlierEraCascadeAccepted() {
        var cascade = submission(Faction.ERASERS, SpecialAction.CASCADE);

        assertThatCode(() -> cascade.validate(MAX_ERAS - 1, 1, MAX_ERAS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("other event-targeting specials stay playable in the final era")
    void finalEraAnnihilateAccepted() {
        var annihilate = submission(Faction.ERASERS, SpecialAction.ANNIHILATE);

        assertThatCode(() -> annihilate.validate(MAX_ERAS, 1, MAX_ERAS)).doesNotThrowAnyException();
    }

    private static SubmittedAction.SpecialActionSubmission submission(Faction faction, SpecialAction specialAction) {
        return new SubmittedAction.SpecialActionSubmission(
                UUID.randomUUID(), faction, specialAction, null, null, UUID.randomUUID(), UUID.randomUUID(), null);
    }
}
