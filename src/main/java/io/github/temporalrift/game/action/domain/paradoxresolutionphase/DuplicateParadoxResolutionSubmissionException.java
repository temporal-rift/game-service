package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import java.util.UUID;

public class DuplicateParadoxResolutionSubmissionException extends RuntimeException {

    public DuplicateParadoxResolutionSubmissionException(UUID playerId) {
        super("Player " + playerId + " already submitted a paradox resolution card");
    }
}
