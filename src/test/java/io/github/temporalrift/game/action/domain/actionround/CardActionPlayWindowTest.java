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
                    .isThrownBy(action::validate)
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

        assertThatCode(action::validate).doesNotThrowAnyException();
    }

    private SubmittedAction.CardAction cardAction(CardType cardType) {
        return new SubmittedAction.CardAction(
                UUID.randomUUID(), UUID.randomUUID(), cardType, UUID.randomUUID(), null, UUID.randomUUID());
    }
}
