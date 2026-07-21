package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

public class UnknownActionTargetException extends RuntimeException {

    public UnknownActionTargetException(UUID targetId) {
        super("Action target " + targetId + " does not belong to the current game/era");
    }
}
