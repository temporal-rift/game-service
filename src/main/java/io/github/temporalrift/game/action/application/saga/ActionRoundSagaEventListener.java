package io.github.temporalrift.game.action.application.saga;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.action.CardPlayed;
import io.github.temporalrift.events.action.SpecialActionPlayed;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

@Component
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class ActionRoundSagaEventListener {

    private final ActionRoundSaga saga;

    ActionRoundSagaEventListener(ActionRoundSaga saga) {
        this.saga = saga;
    }

    @ApplicationModuleListener
    void onStartActionRound(StartActionRoundApplicationEvent event) {
        saga.start(event.gameId(), event.eraNumber(), event.roundNumber(), event.playerIds());
    }

    @ApplicationModuleListener
    void onCardPlayed(CardPlayed event) {
        saga.handlePlayerSubmitted(event.gameId(), event.eraNumber(), event.roundNumber(), event.playerId());
    }

    @ApplicationModuleListener
    void onSpecialActionPlayed(SpecialActionPlayed event) {
        saga.handlePlayerSubmitted(event.gameId(), event.eraNumber(), event.roundNumber(), event.playerId());
    }
}
