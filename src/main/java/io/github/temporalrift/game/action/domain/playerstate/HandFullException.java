package io.github.temporalrift.game.action.domain.playerstate;

public class HandFullException extends RuntimeException {

    public HandFullException(int maxCards) {
        super("Hand is full; maximum is " + maxCards + " cards");
    }
}
