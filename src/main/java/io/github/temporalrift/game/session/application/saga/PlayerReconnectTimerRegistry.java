package io.github.temporalrift.game.session.application.saga;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.stereotype.Component;

@Component
class PlayerReconnectTimerRegistry {

    private final Map<UUID, ScheduledFuture<?>> scheduledTimers = new ConcurrentHashMap<>();

    void register(UUID sagaId, ScheduledFuture<?> future) {
        if (future == null) {
            return;
        }
        // Replacing a still-armed timer must cancel it, or the orphan fires anyway.
        var previous = scheduledTimers.put(sagaId, future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    void remove(UUID sagaId) {
        scheduledTimers.remove(sagaId);
    }

    void cancel(UUID sagaId) {
        var future = scheduledTimers.remove(sagaId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
