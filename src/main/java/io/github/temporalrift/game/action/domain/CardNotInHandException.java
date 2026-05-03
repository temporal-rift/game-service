package io.github.temporalrift.game.action.domain;

import java.util.UUID;

public class CardNotInHandException extends RuntimeException {

    public CardNotInHandException(UUID cardInstanceId) {
        super("Card not in player's hand: " + cardInstanceId);
    }
}
