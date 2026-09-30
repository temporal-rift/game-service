package io.github.temporalrift.game.action.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;

public interface ParadoxResolutionPhaseRepository {

    ParadoxResolutionPhase save(ParadoxResolutionPhase phase);

    Optional<ParadoxResolutionPhase> findByGameIdAndEraNumber(UUID gameId, int eraNumber);

    Optional<ParadoxResolutionPhase> findByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber);

    /**
     * Serializes, until the current transaction ends, the opening of the game-era's phase with the adoption of a
     * participant into it. Whichever runs second sees the other's committed phase or participant, so a participant
     * projected while the phase opens is never missed by both.
     */
    void lockParticipantRoster(UUID gameId, int eraNumber);
}
