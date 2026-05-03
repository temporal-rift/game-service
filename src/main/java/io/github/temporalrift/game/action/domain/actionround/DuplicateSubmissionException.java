package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

public class DuplicateSubmissionException extends RuntimeException {

    private final UUID playerId;

    public DuplicateSubmissionException(UUID playerId) {
        super("Player has already submitted an action this round: " + playerId);
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }
}
