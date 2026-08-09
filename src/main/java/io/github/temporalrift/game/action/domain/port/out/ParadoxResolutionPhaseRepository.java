package io.github.temporalrift.game.action.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;

public interface ParadoxResolutionPhaseRepository {

    ParadoxResolutionPhase save(ParadoxResolutionPhase phase);

    Optional<ParadoxResolutionPhase> findByGameIdAndEraNumber(UUID gameId, int eraNumber);

    Optional<ParadoxResolutionPhase> findByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber);
}
