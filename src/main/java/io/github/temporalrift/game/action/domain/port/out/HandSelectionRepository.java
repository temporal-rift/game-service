package io.github.temporalrift.game.action.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.handselection.HandSelection;

public interface HandSelectionRepository {
    HandSelection save(HandSelection selection);

    /**
     * Persists a newly opened selection only when no selection already exists for its game, era, and player.
     *
     * <p>This operation is safe to retry after duplicate {@code HandDealt} delivery. A {@code false} result means
     * another delivery already created the selection, so callers must not schedule another expiry task.
     *
     * @param selection a newly opened selection
     * @return {@code true} when this invocation created the selection; {@code false} when it already existed
     */
    boolean createIfAbsent(HandSelection selection);

    Optional<HandSelection> findByGameIdAndEraNumberAndPlayerIdWithLock(UUID gameId, int eraNumber, UUID playerId);

    List<UUID> findOpenDueIds(Instant now);

    Optional<HandSelection> findByIdWithLock(UUID id);
}
