package io.github.temporalrift.game.shared;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Tracks in-memory {@link ScheduledFuture}s keyed by saga ID so a saga's timer can be cancelled or
 * dropped from bookkeeping without reaching into the scheduler. Holds no domain knowledge — each
 * module wires its own instance (e.g. as a {@code @Component}) rather than sharing state across
 * module boundaries.
 */
public class ScheduledTaskRegistry {

    private final Map<UUID, ScheduledFuture<?>> scheduledTimers = new ConcurrentHashMap<>();

    public void register(UUID sagaId, ScheduledFuture<?> future) {
        if (future == null) {
            return;
        }
        // Replacing a still-armed timer must cancel it, or the orphan fires anyway.
        var previous = scheduledTimers.put(sagaId, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public void remove(UUID sagaId) {
        scheduledTimers.remove(sagaId);
    }

    public void cancel(UUID sagaId) {
        var future = scheduledTimers.remove(sagaId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
