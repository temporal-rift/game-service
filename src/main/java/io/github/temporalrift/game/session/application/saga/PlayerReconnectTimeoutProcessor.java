package io.github.temporalrift.game.session.application.saga;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PlayerReconnectTimeoutProcessor {

    private final PlayerReconnectSagaImpl saga;

    PlayerReconnectTimeoutProcessor(PlayerReconnectSagaImpl saga) {
        this.saga = saga;
    }

    @Transactional(propagation = REQUIRES_NEW)
    void handleTimerExpiry(UUID sagaId) {
        saga.handleTimerExpiry(sagaId);
    }
}
