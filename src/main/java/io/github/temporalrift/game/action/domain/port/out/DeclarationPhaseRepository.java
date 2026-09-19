package io.github.temporalrift.game.action.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhase;

public interface DeclarationPhaseRepository {

    DeclarationPhase save(DeclarationPhase phase);

    /**
     * Persists a newly opened phase, returning {@code false} when a phase for the same game and era
     * already exists (duplicate open facts converge on the existing phase).
     */
    boolean createIfAbsent(DeclarationPhase phase);

    Optional<DeclarationPhase> findByGameIdAndEraNumber(UUID gameId, int eraNumber);

    Optional<DeclarationPhase> findByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber);

    Optional<DeclarationPhase> findByIdWithLock(UUID id);

    List<UUID> findOpenDueIds(Instant now);
}
