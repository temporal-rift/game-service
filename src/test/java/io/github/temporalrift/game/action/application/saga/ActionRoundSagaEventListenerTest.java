package io.github.temporalrift.game.action.application.saga;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.action.CardPlayed;
import io.github.temporalrift.events.action.SpecialActionPlayed;
import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.game.action.StartActionRoundRequested;

@ExtendWith(MockitoExtension.class)
class ActionRoundSagaEventListenerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_ID = UUID.randomUUID();
    static final int ERA_NUMBER = 1;
    static final int ROUND_NUMBER = 2;

    @Mock
    ActionRoundSaga saga;

    @Mock
    ActionRoundTimerScheduler timerScheduler;

    @Test
    @DisplayName("onStartActionRound delegates to saga and schedules timer after commit")
    void onStartActionRound_delegatesToSagaAndSchedulesTimer() {
        // given
        var listener = new ActionRoundSagaEventListener(saga, timerScheduler);
        var playerIds = List.of(PLAYER_ID, UUID.randomUUID());
        var event = new StartActionRoundRequested(GAME_ID, ERA_NUMBER, ROUND_NUMBER, playerIds);
        var result = new ActionRoundSaga.StartResult(UUID.randomUUID(), Instant.parse("2099-01-01T00:00:30Z"));
        given(saga.start(GAME_ID, ERA_NUMBER, ROUND_NUMBER, playerIds)).willReturn(result);

        // when
        listener.onStartActionRound(event);

        // then
        then(saga).should().start(GAME_ID, ERA_NUMBER, ROUND_NUMBER, playerIds);
        then(timerScheduler).should().scheduleAfterCommit(result);
    }

    @Test
    @DisplayName("onCardPlayed delegates player submission to the saga")
    void onCardPlayed_delegatesToSaga() {
        // given
        var listener = new ActionRoundSagaEventListener(saga, timerScheduler);
        var event = new CardPlayed(
                GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_ID, UUID.randomUUID(), CardType.PUSH, null, null, null);

        // when
        listener.onCardPlayed(event);

        // then
        then(saga).should().handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_ID);
    }

    @Test
    @DisplayName("onSpecialActionPlayed delegates player submission to the saga")
    void onSpecialActionPlayed_delegatesToSaga() {
        // given
        var listener = new ActionRoundSagaEventListener(saga, timerScheduler);
        var event = new SpecialActionPlayed(
                GAME_ID,
                ERA_NUMBER,
                ROUND_NUMBER,
                PLAYER_ID,
                Faction.ERASERS,
                SpecialAction.ANNIHILATE,
                null,
                null,
                null);

        // when
        listener.onSpecialActionPlayed(event);

        // then
        then(saga).should().handlePlayerSubmitted(GAME_ID, ERA_NUMBER, ROUND_NUMBER, PLAYER_ID);
    }
}
