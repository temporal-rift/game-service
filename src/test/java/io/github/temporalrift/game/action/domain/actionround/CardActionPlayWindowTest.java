package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

class CardActionPlayWindowTest {

    private static final Set<CardType> PLAYER_TARGETING_CARD_TYPES =
            Set.of(CardType.NULLIFY, CardType.REDIRECT, CardType.AMPLIFY, CardType.JAM, CardType.INTERCEPT);

    @Test
    void stabilizeAndDetonateAreRejectedDuringActionRounds() {
        for (var cardType : new CardType[] {CardType.STABILIZE, CardType.DETONATE}) {
            var action = cardAction(cardType);

            assertThatExceptionOfType(CardNotEligibleForActionRoundException.class)
                    .isThrownBy(() -> action.validate(1, 1))
                    .withMessageContaining(cardType.name());
        }
    }

    @Test
    void collideRemainsEligibleDuringActionRounds() {
        var action = new SubmittedAction.CardAction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CardType.COLLIDE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID());

        assertThatCode(() -> action.validate(1, 1)).doesNotThrowAnyException();
    }

    @Test
    void traceIsRejectedOnlyInEraOneRoundOne() {
        var action = cardAction(CardType.TRACE);

        assertThatExceptionOfType(CardNotEligibleForRoundException.class)
                .isThrownBy(() -> action.validate(1, 1))
                .withMessageContaining("TRACE");
    }

    @Test
    void traceIsAcceptedInEraTwoRoundOne() {
        var action = cardAction(CardType.TRACE);

        assertThatCode(() -> action.validate(2, 1)).doesNotThrowAnyException();
    }

    @Test
    void traceIsAcceptedInEraOneRoundTwoAndThree() {
        var action = cardAction(CardType.TRACE);

        assertThatCode(() -> action.validate(1, 2)).doesNotThrowAnyException();
        assertThatCode(() -> action.validate(1, 3)).doesNotThrowAnyException();
    }

    @Test
    void jamScanInterceptAreRejectedInRoundThreeOfAnyEra() {
        for (var cardType : new CardType[] {CardType.JAM, CardType.SCAN, CardType.INTERCEPT}) {
            var action = cardAction(cardType);

            for (var era : new int[] {1, 2}) {
                assertThatExceptionOfType(CardNotEligibleForRoundException.class)
                        .isThrownBy(() -> action.validate(era, 3))
                        .withMessageContaining(cardType.name());
            }
        }
    }

    @Test
    void jamScanInterceptAreAcceptedInRoundsOneAndTwo() {
        for (var cardType : new CardType[] {CardType.JAM, CardType.SCAN, CardType.INTERCEPT}) {
            var action = cardAction(cardType);

            assertThatCode(() -> action.validate(1, 1)).doesNotThrowAnyException();
            assertThatCode(() -> action.validate(2, 2)).doesNotThrowAnyException();
        }
    }

    @Test
    void playerTargetingCardWithoutTargetPlayerIsRejected() {
        for (var cardType : PLAYER_TARGETING_CARD_TYPES) {
            var playerId = UUID.randomUUID();
            var action = new SubmittedAction.CardAction(
                    playerId, UUID.randomUUID(), cardType, CardGrade.I, null, null, null, null);

            assertThatExceptionOfType(InvalidActionTargetException.class)
                    .isThrownBy(() -> action.validate(1, 1))
                    .withMessageContaining(cardType.name());
        }
    }

    @Test
    void playerTargetingCardCarryingAnEventTargetIsRejected() {
        for (var cardType : PLAYER_TARGETING_CARD_TYPES) {
            var playerId = UUID.randomUUID();
            var action = new SubmittedAction.CardAction(
                    playerId, UUID.randomUUID(), cardType, CardGrade.I, UUID.randomUUID(), null, null, null);

            assertThatExceptionOfType(InvalidActionTargetException.class)
                    .isThrownBy(() -> action.validate(1, 1))
                    .withMessageContaining(cardType.name());
        }
    }

    @Test
    void playerTargetingCardCarryingAnOutcomeTargetIsRejected() {
        for (var cardType : PLAYER_TARGETING_CARD_TYPES) {
            var playerId = UUID.randomUUID();
            var withTargetOutcome = new SubmittedAction.CardAction(
                    playerId,
                    UUID.randomUUID(),
                    cardType,
                    CardGrade.I,
                    null,
                    null,
                    UUID.randomUUID(),
                    UUID.randomUUID());
            var withSourceOutcome = new SubmittedAction.CardAction(
                    playerId,
                    UUID.randomUUID(),
                    cardType,
                    CardGrade.I,
                    null,
                    UUID.randomUUID(),
                    null,
                    UUID.randomUUID());

            assertThatExceptionOfType(InvalidActionTargetException.class)
                    .isThrownBy(() -> withTargetOutcome.validate(1, 1))
                    .withMessageContaining(cardType.name());
            assertThatExceptionOfType(InvalidActionTargetException.class)
                    .isThrownBy(() -> withSourceOutcome.validate(1, 1))
                    .withMessageContaining(cardType.name());
        }
    }

    @Test
    void eventTargetingCardWithoutTargetEventIsRejected() {
        for (var cardType : new CardType[] {CardType.PUSH, CardType.SUPPRESS, CardType.SCAN, CardType.DECOY}) {
            var action = new SubmittedAction.CardAction(
                    UUID.randomUUID(), UUID.randomUUID(), cardType, CardGrade.I, null, null, null, null);

            assertThatExceptionOfType(InvalidActionTargetException.class)
                    .isThrownBy(() -> action.validate(1, 1))
                    .withMessageContaining(cardType.name());
        }
    }

    @Test
    void eventTargetingCardCarryingAPlayerTargetIsRejected() {
        var action = new SubmittedAction.CardAction(
                UUID.randomUUID(), UUID.randomUUID(), CardType.PUSH, CardGrade.I, null, null, null, UUID.randomUUID());

        assertThatExceptionOfType(InvalidActionTargetException.class)
                .isThrownBy(() -> action.validate(1, 1))
                .withMessageContaining("PUSH");
    }

    @Test
    void selfTargetingIsRejected() {
        for (var cardType : PLAYER_TARGETING_CARD_TYPES) {
            var playerId = UUID.randomUUID();
            var action = new SubmittedAction.CardAction(
                    playerId, UUID.randomUUID(), cardType, CardGrade.I, null, null, null, playerId);

            assertThatExceptionOfType(InvalidActionTargetException.class)
                    .isThrownBy(() -> action.validate(1, 1))
                    .withMessageContaining(cardType.name());
        }
    }

    @Test
    void playerTargetingCardWithAnOpponentTargetValidates() {
        for (var cardType : PLAYER_TARGETING_CARD_TYPES) {
            var action = new SubmittedAction.CardAction(
                    UUID.randomUUID(), UUID.randomUUID(), cardType, CardGrade.I, null, null, null, UUID.randomUUID());

            assertThatCode(() -> action.validate(1, 1)).doesNotThrowAnyException();
        }
    }

    private SubmittedAction.CardAction cardAction(CardType cardType) {
        if (PLAYER_TARGETING_CARD_TYPES.contains(cardType)) {
            return new SubmittedAction.CardAction(
                    UUID.randomUUID(), UUID.randomUUID(), cardType, CardGrade.I, null, null, null, UUID.randomUUID());
        }
        return new SubmittedAction.CardAction(
                UUID.randomUUID(), UUID.randomUUID(), cardType, UUID.randomUUID(), null, UUID.randomUUID());
    }
}
