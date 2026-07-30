package io.github.temporalrift.game.action.domain.activisterastate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.shared.CardType;

class ActivistEraStateTest {

    @Test
    void declaration_isRecordedOnlyOncePerEra() {
        var state = state(false);

        state.declare(ActivistDeclarationMode.RALLY, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> state.declare(ActivistDeclarationMode.RALLY, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ActivistDeclarationAlreadyRecordedException.class);
    }

    @Test
    void momentum_requiresPreviousSuccessfulDeclaration() {
        var state = state(false);

        assertThatThrownBy(() -> state.declare(ActivistDeclarationMode.MOMENTUM, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(MomentumNotEligibleException.class);
    }

    @Test
    void exposeBehaviorChangesOnlyForDifferentQualifyingRoundThreeSignature() {
        var state = state(false);
        var roundOneSignature =
                new ProbabilityInfluenceSignature(CardType.PUSH, UUID.randomUUID(), null, UUID.randomUUID());
        state.expose(UUID.randomUUID(), roundOneSignature);

        assertThat(state.recordExposeBehaviorChanged(roundOneSignature)).isFalse();
        assertThat(state.recordExposeBehaviorChanged(new ProbabilityInfluenceSignature(
                        CardType.SUPPRESS,
                        roundOneSignature.targetEventId(),
                        null,
                        roundOneSignature.targetOutcomeId())))
                .isTrue();
        assertThat(state.recordExposeBehaviorChanged(new ProbabilityInfluenceSignature(
                        CardType.SWING,
                        roundOneSignature.targetEventId(),
                        UUID.randomUUID(),
                        roundOneSignature.targetOutcomeId())))
                .isFalse();
    }

    @Test
    void nonProbabilityCardsDoNotProduceExposeSignatures() {
        var action = new SubmittedAction.CardAction(
                UUID.randomUUID(), UUID.randomUUID(), CardType.INTERCEPT, UUID.randomUUID(), null, UUID.randomUUID());

        assertThat(ProbabilityInfluenceSignature.from(action)).isEmpty();
    }

    private ActivistEraState state(boolean momentumEligible) {
        return new ActivistEraState(UUID.randomUUID(), UUID.randomUUID(), 2, UUID.randomUUID(), momentumEligible);
    }
}
