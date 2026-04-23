package io.github.temporalrift.game.session.application.saga;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class StartGameSagaDisconnectListener {

    private final StartGameSagaCompensator compensator;

    StartGameSagaDisconnectListener(StartGameSagaCompensator compensator) {
        this.compensator = compensator;
    }

    @EventListener
    void onPlayerDisconnected(PlayerDisconnectedApplicationEvent event) {
        compensator.cancel(event.gameId());
    }
}
