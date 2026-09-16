package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import java.util.UUID;

public class ParadoxResolutionPhaseNotFoundException extends RuntimeException {

    public ParadoxResolutionPhaseNotFoundException(UUID gameId, int eraNumber) {
        super("No paradox resolution phase found for game " + gameId + " era " + eraNumber);
    }
}
