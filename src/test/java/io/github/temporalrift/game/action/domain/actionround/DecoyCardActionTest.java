package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.temporalrift.game.action.domain.event.CardPlayed;
import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

class DecoyCardActionTest {

    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID CARD_ID = UUID.randomUUID();
    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID OUTCOME_ID = UUID.randomUUID();
    private static final UUID OTHER_PLAYER = UUID.randomUUID();

    @ParameterizedTest
    @EnumSource(CardCategory.class)
    void acceptsAnyDisguiseWithoutTargets(CardCategory disguise) {
        var action = decoy(disguise);

        assertThatCode(() -> action.validate(1, 1)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingDisguise() {
        var action = decoy(null);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> action.validate(1, 1))
                .withMessageContaining("disguiseCategory");
    }

    @Test
    void rejectsEveryTargetField() {
        var targeted = List.of(
                card(CardType.DECOY, EVENT_ID, null, null, null, null, null, CardCategory.DISRUPTION),
                card(CardType.DECOY, null, List.of(EVENT_ID), null, null, null, null, CardCategory.DISRUPTION),
                card(CardType.DECOY, null, null, OUTCOME_ID, null, null, null, CardCategory.DISRUPTION),
                card(CardType.DECOY, null, null, null, OUTCOME_ID, null, null, CardCategory.DISRUPTION),
                card(CardType.DECOY, null, null, null, null, OTHER_PLAYER, null, CardCategory.DISRUPTION),
                card(CardType.DECOY, null, null, null, null, null, List.of(OTHER_PLAYER), CardCategory.DISRUPTION));

        targeted.forEach(action -> assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> action.validate(1, 1))
                .withMessageContaining("cannot carry any target"));
    }

    @Test
    void rejectsDisguiseOnAnyOtherCard() {
        var push = card(CardType.PUSH, EVENT_ID, null, null, OUTCOME_ID, null, null, CardCategory.DISRUPTION);

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> push.validate(1, 1))
                .withMessageContaining("PUSH cannot carry a disguiseCategory");
    }

    @Test
    void cardPlayedCarriesTheDisguiseAndNoTarget() {
        var played = (CardPlayed) decoy(CardCategory.PARADOX).toPlayedEvent(GAME_ID, 1, 2);

        assertThat(played.cardType()).isEqualTo(CardType.DECOY);
        assertThat(played.disguiseCategory()).isEqualTo(CardCategory.PARADOX);
        assertThat(played.targetEventId()).isNull();
        assertThat(played.targetEventIds()).isNull();
        assertThat(played.targetOutcomeId()).isNull();
        assertThat(played.targetPlayerId()).isNull();
        assertThat(played.targetPlayerIds()).isNull();
    }

    @Test
    void otherCardsPlayWithoutDisguise() {
        var push = new SubmittedAction.CardAction(PLAYER_ID, CARD_ID, CardType.PUSH, EVENT_ID, null, OUTCOME_ID);

        assertThat(((CardPlayed) push.toPlayedEvent(GAME_ID, 1, 1)).disguiseCategory())
                .isNull();
    }

    @Test
    void decoyIsSummarizedAsItsDisguise() {
        var action = decoy(CardCategory.PROBABILITY_SHIFTER);

        assertThat(action.family()).isEqualTo(ActionFamily.CARD);
        assertThat(action.publicCategory()).contains(CardCategory.PROBABILITY_SHIFTER);
    }

    @ParameterizedTest
    @EnumSource(
            value = CardType.class,
            names = {"DECOY", "STABILIZE", "DETONATE"},
            mode = EnumSource.Mode.EXCLUDE)
    void everyOtherActionRoundCardIsSummarizedByItsOwnCategory(CardType cardType) {
        var action = card(cardType, EVENT_ID, null, null, OUTCOME_ID, null, null, null);

        assertThat(action.family()).isEqualTo(ActionFamily.CARD);
        assertThat(action.publicCategory()).contains(cardType.getCategory());
    }

    @ParameterizedTest
    @EnumSource(SpecialAction.class)
    void specialsAreSummarizedWithoutCategory(SpecialAction specialAction) {
        var action = new SubmittedAction.SpecialActionSubmission(
                PLAYER_ID, Faction.ERASERS, specialAction, null, null, EVENT_ID, OUTCOME_ID, null);

        assertThat(action.family()).isEqualTo(ActionFamily.SPECIAL);
        assertThat(action.publicCategory()).isEmpty();
    }

    private static SubmittedAction.CardAction decoy(CardCategory disguise) {
        return card(CardType.DECOY, null, null, null, null, null, null, disguise);
    }

    private static SubmittedAction.CardAction card(
            CardType cardType,
            UUID targetEventId,
            List<UUID> targetEventIds,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId,
            List<UUID> targetPlayerIds,
            CardCategory disguise) {
        return new SubmittedAction.CardAction(
                PLAYER_ID,
                CARD_ID,
                cardType,
                CardGrade.I,
                targetEventId,
                targetEventIds,
                sourceOutcomeId,
                targetOutcomeId,
                targetPlayerId,
                targetPlayerIds,
                disguise);
    }
}
