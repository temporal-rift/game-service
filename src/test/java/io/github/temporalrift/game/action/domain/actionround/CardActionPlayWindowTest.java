package io.github.temporalrift.game.action.domain.actionround;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.shared.CardType;

class CardActionPlayWindowTest {

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

    private SubmittedAction.CardAction cardAction(CardType cardType) {
        return new SubmittedAction.CardAction(
                UUID.randomUUID(), UUID.randomUUID(), cardType, UUID.randomUUID(), null, UUID.randomUUID());
    }
}
