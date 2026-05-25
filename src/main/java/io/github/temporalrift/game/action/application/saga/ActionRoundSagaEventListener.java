package io.github.temporalrift.game.action.application.saga;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.action.CardPlayed;
import io.github.temporalrift.events.action.SpecialActionPlayed;
import io.github.temporalrift.game.action.StartActionRoundRequested;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;

/**
 * Bridges typed Spring events into the action-round saga.
 *
 * <p>Keeping this translation in one place makes it safer to evolve the saga without spreading event
 * wiring across command handlers and infrastructure adapters.
 */
@Component
@ConditionalOnBean({ActionRoundRepository.class, PlayerStateRepository.class})
class ActionRoundSagaEventListener {

    private final ActionRoundSaga saga;
    private final ActionRoundTimerScheduler timerScheduler;

    ActionRoundSagaEventListener(ActionRoundSaga saga, ActionRoundTimerScheduler timerScheduler) {
        this.saga = saga;
        this.timerScheduler = timerScheduler;
    }

    @ApplicationModuleListener
    void onStartActionRound(StartActionRoundRequested event) {
        var startResult = saga.start(event.gameId(), event.eraNumber(), event.roundNumber(), event.playerIds());
        timerScheduler.scheduleAfterCommit(startResult);
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
