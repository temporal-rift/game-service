package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.shared.ScheduledTaskRegistry;

/**
 * Module-scoped instance of {@link ScheduledTaskRegistry}. A shared Spring bean would mix this
 * module's reconnect timers with the action module's round timers in one map — modules never share
 * mutable state, only events — so each module keeps its own instance behind its own bean type.
 */
@Component
class PlayerReconnectTimerRegistry {

    private final ScheduledTaskRegistry delegate = new ScheduledTaskRegistry();

    void register(UUID sagaId, ScheduledFuture<?> future) {
        delegate.register(sagaId, future);
    }

    void remove(UUID sagaId) {
        delegate.remove(sagaId);
    }

    void cancel(UUID sagaId) {
        delegate.cancel(sagaId);
    }
}
