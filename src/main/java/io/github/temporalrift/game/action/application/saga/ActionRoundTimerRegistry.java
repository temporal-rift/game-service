package io.github.temporalrift.game.action.application.saga;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.stereotype.Component;

@Component
class ActionRoundTimerRegistry {

    private final Map<UUID, ScheduledFuture<?>> scheduledTimers = new ConcurrentHashMap<>();

    void register(UUID sagaId, ScheduledFuture<?> future) {
        if (future != null) {
            scheduledTimers.put(sagaId, future);
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
