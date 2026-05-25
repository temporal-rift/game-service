package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Restores in-flight action-round timers after application startup.
 *
 * <p>WAITING sagas are either rescheduled or expired immediately, while CLOSING sagas resume the
 * close path so a crash cannot leave the round permanently half-finished.
 */
@Component
@ConditionalOnBean(ActionRoundSaga.class)
class ActionRoundSagaRecovery implements ApplicationListener<ApplicationStartedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ActionRoundSagaRecovery.class);

    private final ActionRoundSagaStateManager stateManager;
    private final ActionRoundTimerScheduler timerScheduler;
    private final ActionRoundTimeoutProcessor timeoutProcessor;

    ActionRoundSagaRecovery(
            ActionRoundSagaStateManager stateManager,
            ActionRoundTimerScheduler timerScheduler,
            ActionRoundTimeoutProcessor timeoutProcessor) {
        this.stateManager = stateManager;
        this.timerScheduler = timerScheduler;
        this.timeoutProcessor = timeoutProcessor;
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
                timeoutProcessor.handleTimerExpiry(state.sagaId());
            } else {
                timerScheduler.reschedule(state.sagaId(), state.timerExpiresAt());
            }
        }

        // Recover CLOSING sagas
        for (var state : stateManager.findAllClosing()) {
            log.info("Recovering CLOSING saga {} — resuming tryClose", state.sagaId());
            timeoutProcessor.handleTimerExpiry(state.sagaId());
        }
    }
}
