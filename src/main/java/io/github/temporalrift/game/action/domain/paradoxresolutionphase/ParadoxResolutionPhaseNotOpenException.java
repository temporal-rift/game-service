package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import java.util.UUID;

public class ParadoxResolutionPhaseNotOpenException extends RuntimeException {

    public ParadoxResolutionPhaseNotOpenException(UUID gameId, int eraNumber) {
        super("Paradox resolution phase is not open for game " + gameId + " era " + eraNumber);
    }
}
