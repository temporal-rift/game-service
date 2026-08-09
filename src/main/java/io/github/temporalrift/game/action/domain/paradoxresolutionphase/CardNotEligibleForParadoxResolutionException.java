package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import io.github.temporalrift.game.shared.CardType;

public class CardNotEligibleForParadoxResolutionException extends RuntimeException {

    public CardNotEligibleForParadoxResolutionException(CardType cardType) {
        super(cardType + " cannot be played during paradox resolution");
    }
}
