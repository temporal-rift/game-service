package io.github.temporalrift.game.session.application.saga;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class StartGameSagaDisconnectListener {

    private final StartGameSagaCompensator compensator;

    StartGameSagaDisconnectListener(StartGameSagaCompensator compensator) {
        this.compensator = compensator;
    }

    @ApplicationModuleListener
    void onPlayerDisconnected(PlayerDisconnectedApplicationEvent event) {
        compensator.cancel(event.gameId());
    }
}
