package io.github.temporalrift.game.session.application.saga;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.ActionRoundClosed;
import io.github.temporalrift.game.scoring.ScoresUpdated;
import io.github.temporalrift.game.session.domain.event.EraStarted;

@Component
class EraSagaEventListener {

    private final EraSaga eraSaga;
    private final EraSagaAdvancer eraSagaAdvancer;

    EraSagaEventListener(EraSaga eraSaga, EraSagaAdvancer eraSagaAdvancer) {
        this.eraSaga = eraSaga;
        this.eraSagaAdvancer = eraSagaAdvancer;
    }

    @ApplicationModuleListener
    void onEraStarted(EraStarted event) {
        eraSaga.start(event.gameId(), event.eraNumber(), event.playerIds(), event.cascadedEventIds());
    }

    @ApplicationModuleListener
    void onActionRoundClosed(ActionRoundClosed event) {
        eraSagaAdvancer.handleRoundClosed(event.gameId(), event);
    }

    @ApplicationModuleListener
    void onScoresUpdated(ScoresUpdated event) {
        eraSagaAdvancer.handleScoresUpdated(event.gameId(), event);
    }
}
