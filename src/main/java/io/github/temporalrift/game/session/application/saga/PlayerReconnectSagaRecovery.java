package io.github.temporalrift.game.session.application.saga;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
class PlayerReconnectSagaRecovery implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger log = LoggerFactory.getLogger(PlayerReconnectSagaRecovery.class);

    private final PlayerReconnectSagaStateManager stateManager;
    private final PlayerReconnectSagaImpl saga;

    PlayerReconnectSagaRecovery(PlayerReconnectSagaStateManager stateManager, PlayerReconnectSagaImpl saga) {
        this.stateManager = stateManager;
        this.saga = saga;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        var now = Instant.now();
        var inFlight = stateManager.findAllInGracePeriod();

        if (inFlight.isEmpty()) {
            return;
        }

        log.info("Recovering {} in-flight PlayerReconnectSaga(s) after restart", inFlight.size());

        for (var state : inFlight) {
            if (state.graceExpiresAt().isBefore(now)) {
                log.info("Grace period already expired for saga {} — processing immediately", state.sagaId());
                saga.handleTimerExpiry(state.sagaId());
            } else {
                log.info("Rescheduling timer for saga {} expiring at {}", state.sagaId(), state.graceExpiresAt());
                saga.rescheduleTimer(state.sagaId(), state.graceExpiresAt());
            }
        }
    }
}
