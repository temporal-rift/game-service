package io.github.temporalrift.game.action.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.handselection.HandSelection;

public interface HandSelectionRepository {
    HandSelection save(HandSelection selection);

    Optional<HandSelection> findByGameIdAndEraNumberAndPlayerIdWithLock(UUID gameId, int eraNumber, UUID playerId);

    List<UUID> findOpenDueIds(Instant now);

    Optional<HandSelection> findByIdWithLock(UUID id);
}
