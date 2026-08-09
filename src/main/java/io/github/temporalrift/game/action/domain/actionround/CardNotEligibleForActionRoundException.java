package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.CardType;

public class CardNotEligibleForActionRoundException extends RuntimeException {

    public CardNotEligibleForActionRoundException(CardType cardType) {
        super(cardType + " cannot be played during an action round");
    }
}
