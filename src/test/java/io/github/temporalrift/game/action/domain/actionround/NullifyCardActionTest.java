package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

class NullifyCardActionTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID CARD_ID = UUID.randomUUID();
    private static final UUID TARGET_1 = UUID.randomUUID();
    private static final UUID TARGET_2 = UUID.randomUUID();

    @Test
    void everyGradeAcceptsItsRequiredTargetCount() {
        assertThatCode(() -> nullify(CardGrade.I, List.of(TARGET_1)).validate(1, 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> nullify(CardGrade.II, List.of(TARGET_1, TARGET_2)).validate(1, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingListAndGradeCountMismatch() {
        var missing = nullify(CardGrade.I, null);
        var tooMany = nullify(CardGrade.I, List.of(TARGET_1, TARGET_2));
        var tooFew = nullify(CardGrade.II, List.of(TARGET_1));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> missing.validate(1, 1))
                .withMessageContaining("targetPlayerIds");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> tooMany.validate(1, 1))
                .withMessageContaining("exactly 1");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> tooFew.validate(1, 1))
                .withMessageContaining("exactly 2");
    }

    @Test
    void rejectsDuplicateAndSelfTargets() {
        var duplicate = nullify(CardGrade.II, List.of(TARGET_1, TARGET_1));
        var selfTarget = nullify(CardGrade.I, List.of(PLAYER_ID));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> duplicate.validate(1, 1))
                .withMessageContaining("distinct");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> selfTarget.validate(1, 1))
                .withMessageContaining("NULLIFY");
    }

    @Test
    void rejectsScalarEventOutcomeAndPlayerMixes() {
        var scalarPlayer = new SubmittedAction.CardAction(
                PLAYER_ID, CARD_ID, CardType.NULLIFY, CardGrade.I, null, null, null, TARGET_1);
        var eventTarget = new SubmittedAction.CardAction(
                PLAYER_ID,
                CARD_ID,
                CardType.NULLIFY,
                CardGrade.II,
                TARGET_1,
                null,
                null,
                null,
                null,
                List.of(TARGET_1, TARGET_2),
                null);
        var outcomeTarget = new SubmittedAction.CardAction(
                PLAYER_ID,
                CARD_ID,
                CardType.NULLIFY,
                CardGrade.II,
                null,
                null,
                TARGET_1,
                TARGET_2,
                null,
                List.of(TARGET_1, TARGET_2),
                null);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> scalarPlayer.validate(1, 1))
                .withMessageContaining("scalar");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> eventTarget.validate(1, 1))
                .withMessageContaining("scalar");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> outcomeTarget.validate(1, 1))
                .withMessageContaining("scalar");
    }

    @Test
    void otherCardsRejectThePlayerTargetList() {
        var push = new SubmittedAction.CardAction(
                PLAYER_ID, CARD_ID, CardType.PUSH, CardGrade.I, null, null, null, null, null, List.of(TARGET_1), null);
        var redirect = new SubmittedAction.CardAction(
                PLAYER_ID,
                CARD_ID,
                CardType.REDIRECT,
                CardGrade.I,
                null,
                null,
                null,
                null,
                TARGET_1,
                List.of(TARGET_1),
                null);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> push.validate(1, 1))
                .withMessageContaining("PUSH");
        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> redirect.validate(1, 1))
                .withMessageContaining("targetPlayerIds");
    }

    @Test
    void unsupportedGradeIsRejected() {
        var action = nullify(CardGrade.III, List.of(TARGET_1, TARGET_2));

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> action.validate(1, 1))
                .withMessageContaining("III");
    }

    @Test
    void toPlayedEventCarriesTheResolvedTargetList() {
        var action = nullify(CardGrade.II, List.of(TARGET_1, TARGET_2));

        var played = (CardPlayed) action.toPlayedEvent(UUID.randomUUID(), 1, 1);

        assertThat(played.targetPlayerId()).isNull();
        assertThat(played.targetPlayerIds()).containsExactly(TARGET_1, TARGET_2);
    }

    private static SubmittedAction.CardAction nullify(CardGrade grade, List<UUID> targets) {
        return new SubmittedAction.CardAction(
                PLAYER_ID, CARD_ID, CardType.NULLIFY, grade, null, null, null, null, null, targets, null);
    }
}
