package io.github.temporalrift.game.action.domain.paradoxresolutionphase;

import java.util.UUID;

/**
 * Raised for a missing paradox-resolution phase and, deliberately, for a caller who is not a
 * participant of the game — the same 404 so outsiders cannot probe which games exist.
 */
public class ParadoxResolutionPhaseNotFoundException extends RuntimeException {

    public ParadoxResolutionPhaseNotFoundException(UUID gameId, int eraNumber) {
        super("No paradox resolution phase found for game " + gameId + " era " + eraNumber);
    }
}
