package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import java.util.UUID;

public class ParadoxResolutionTargetNotAffectedException extends RuntimeException {

    public ParadoxResolutionTargetNotAffectedException(UUID eventId) {
        super("Event " + eventId + " is not affected by the open paradox-resolution phase");
    }
}
