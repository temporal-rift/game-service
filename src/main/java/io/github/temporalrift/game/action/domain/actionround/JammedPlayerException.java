package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

public class JammedPlayerException extends RuntimeException {

    public JammedPlayerException(UUID playerId) {
        super("Player is jammed and cannot play faction specials: " + playerId);
    }
}
