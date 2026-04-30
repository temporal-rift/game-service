package io.github.temporalrift.game.session.application.saga;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.session.EraStarted;

@Component
class EraSagaEventListener {

    private final EraSaga eraSaga;

    EraSagaEventListener(EraSaga eraSaga) {
        this.eraSaga = eraSaga;
    }

    @ApplicationModuleListener
    void onEvent(EventEnvelope envelope) {
        if (envelope.payload() instanceof EraStarted eraStarted) {
            eraSaga.start(
                    eraStarted.gameId(), eraStarted.eraNumber(), eraStarted.playerIds(), eraStarted.cascadedEventIds());
        }
    }
}
