package io.github.temporalrift.game.session.application.saga;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;

/**
 * Database-driven safety net for reconnect grace timers. The in-memory timer scheduled at
 * disconnect is only a latency optimization: it dies with its instance, so the sweep — which any
 * instance can run against the shared saga table — is what guarantees an expired grace period is
 * always processed.
 *
 * <p>Every instance sweeps concurrently without coordination: the timeout processor's status
 * transition ({@code GRACE_PERIOD} only) makes duplicate processing a no-op.
 */
@Component
class PlayerReconnectTimerSweep {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectTimerSweep.class);

    private final PlayerReconnectSagaStateManager stateManager;
    private final PlayerReconnectTimeoutProcessor timeoutProcessor;
    private final Clock clock;

    PlayerReconnectTimerSweep(
            PlayerReconnectSagaStateManager stateManager,
            PlayerReconnectTimeoutProcessor timeoutProcessor,
            Clock clock) {
        this.stateManager = stateManager;
        this.timeoutProcessor = timeoutProcessor;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${game.timers.reconnect-sweep-ms:1000}")
    void sweep() {
        stateManager.findGracePeriodDueBy(clock.instant()).forEach(this::process);
    }

    private void process(PlayerReconnectSagaState state) {
        // One failing saga must not starve the rest of the sweep batch.
        try {
            timeoutProcessor.handleTimerExpiry(state.sagaId());
        } catch (RuntimeException ex) {
            log.error("Reconnect timer sweep failed for saga {} (game {})", state.sagaId(), state.gameId(), ex);
        }
    }
}
