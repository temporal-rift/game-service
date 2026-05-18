package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
class ActionRoundSagaRecovery implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaRecovery.class);

    private final ActionRoundSagaStateManager stateManager;
    private final ActionRoundSagaImpl saga;

    ActionRoundSagaRecovery(ActionRoundSagaStateManager stateManager, ActionRoundSagaImpl saga) {
        this.stateManager = stateManager;
        this.saga = saga;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        var now = Instant.now();

        // Recover WAITING sagas
        for (var state : stateManager.findAllWaiting()) {
            log.info(
                    "Recovering WAITING saga {} game {} era {} round {}",
                    state.sagaId(),
                    state.gameId(),
                    state.eraNumber(),
                    state.roundNumber());
            if (state.timerExpiresAt().isBefore(now)) {
                saga.handleTimerExpiry(state.sagaId());
            } else {
                saga.rescheduleTimer(state.sagaId(), state.timerExpiresAt());
            }
        }

        // Recover CLOSING sagas
        for (var state : stateManager.findAllClosing()) {
            log.info("Recovering CLOSING saga {} — resuming tryClose", state.sagaId());
            saga.handleTimerExpiry(state.sagaId());
        }
    }
}
