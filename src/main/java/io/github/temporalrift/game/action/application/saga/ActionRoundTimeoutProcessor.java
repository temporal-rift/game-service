package io.github.temporalrift.game.action.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedicated transactional entry point for timer callbacks.
 *
 * <p>The scheduler invokes this bean instead of the saga directly so timeout handling always starts
 * in a fresh transaction without relying on self-invocation through Spring proxies.
 */
@Component
@ConditionalOnBean(ActionRoundSagaImpl.class)
class ActionRoundTimeoutProcessor {

    private final ActionRoundSagaImpl saga;

    ActionRoundTimeoutProcessor(ActionRoundSagaImpl saga) {
        this.saga = saga;
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleTimerExpiry(UUID sagaId) {
        saga.handleTimerExpiry(sagaId);
    }
}
