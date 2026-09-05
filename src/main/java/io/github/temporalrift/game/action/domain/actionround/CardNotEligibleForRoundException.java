package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.CardType;

public class CardNotEligibleForRoundException extends RuntimeException {

    public CardNotEligibleForRoundException(CardType cardType, int eraNumber, int roundNumber) {
        super(cardType + " cannot be played in era " + eraNumber + " round " + roundNumber);
    }
}
