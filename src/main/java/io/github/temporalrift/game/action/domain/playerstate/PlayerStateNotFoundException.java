package io.github.temporalrift.game.action.domain.playerstate;

import java.util.UUID;

public class PlayerStateNotFoundException extends RuntimeException {

    public PlayerStateNotFoundException(UUID gameId, UUID playerId) {
        super("No player state found for player " + playerId + " in game " + gameId);
    }
}
