package io.github.temporalrift.game.session.domain.game;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** The mutable state of an in-progress or ended {@link Game}, reconstituted from persistence. */
public record GameProgress(
        int eraCounter,
        Set<UUID> cascadedEventIds,
        UUID pendingCollapsingEventId,
        List<PendingCarryOverEvent> pendingCarryOverEvents,
        Map<UUID, DrawnFutureEvent> drawnEvents,
        GameStatus status) {

    public GameProgress {
        cascadedEventIds = Set.copyOf(Objects.requireNonNull(cascadedEventIds, "cascadedEventIds must not be null"));
        pendingCarryOverEvents =
                List.copyOf(Objects.requireNonNull(pendingCarryOverEvents, "pendingCarryOverEvents must not be null"));
        drawnEvents = Map.copyOf(Objects.requireNonNull(drawnEvents, "drawnEvents must not be null"));
        Objects.requireNonNull(status, "status must not be null");
    }
}
