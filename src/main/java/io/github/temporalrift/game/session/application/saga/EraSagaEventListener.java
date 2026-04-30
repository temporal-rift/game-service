package io.github.temporalrift.game.session.application.saga;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.envelope.EventEnvelope;
import io.github.temporalrift.events.scoring.ScoresUpdated;
import io.github.temporalrift.events.session.EraStarted;

@Component
class EraSagaEventListener {

    private final EraSaga eraSaga;
    private final EraSagaAdvancer eraSagaAdvancer;

    EraSagaEventListener(EraSaga eraSaga, EraSagaAdvancer eraSagaAdvancer) {
        this.eraSaga = eraSaga;
        this.eraSagaAdvancer = eraSagaAdvancer;
    }

    @ApplicationModuleListener
    void onEvent(EventEnvelope envelope) {
        switch (envelope.payload()) {
            case EraStarted(var gameId, var eraNumber, var cascadedEventIds, var playerIds) ->
                eraSaga.start(gameId, eraNumber, playerIds, cascadedEventIds);
            case ActionRoundClosed arc -> eraSagaAdvancer.handleRoundClosed(envelope.gameId(), arc);
            case ScoresUpdated su -> eraSagaAdvancer.handleScoresUpdated(envelope.gameId(), su);
            default -> {}
        }
    }
}
