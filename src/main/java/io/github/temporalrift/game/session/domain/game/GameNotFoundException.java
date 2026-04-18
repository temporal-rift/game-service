package io.github.temporalrift.game.session.domain.game;

import java.util.UUID;

public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(UUID gameId) {
        super("Game not found: " + gameId);
    }
}
