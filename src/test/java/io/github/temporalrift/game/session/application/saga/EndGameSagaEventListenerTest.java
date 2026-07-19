package io.github.temporalrift.game.session.application.saga;

import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.session.domain.event.TimelineCollapsed;
import io.github.temporalrift.game.session.domain.event.TimelineStabilized;
import io.github.temporalrift.game.session.domain.event.WinConditionMet;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;

@ExtendWith(MockitoExtension.class)
class EndGameSagaEventListenerTest {

    @Mock
    EndGameSaga endGameSaga;

    @Test
    void onWinConditionMet_startsSagaForTheWinner() {
        var listener = new EndGameSagaEventListener(endGameSaga);
        var gameId = UUID.randomUUID();
        var winnerId = UUID.randomUUID();

        listener.onWinConditionMet(new WinConditionMet(gameId, winnerId, "ERASERS", 42, "SCORE"));

        then(endGameSaga).should().start(gameId, EndGameTrigger.WIN_CONDITION_MET, winnerId);
    }

    @Test
    void onTimelineCollapsed_startsSagaForWinnersAndLosers() {
        var listener = new EndGameSagaEventListener(endGameSaga);
        var gameId = UUID.randomUUID();
        var winnerId = UUID.randomUUID();
        var loserId = UUID.randomUUID();

        listener.onTimelineCollapsed(new TimelineCollapsed(
                gameId,
                3,
                List.of(new TimelineCollapsed.PlayerFactionResult(winnerId, "WEAVERS")),
                List.of(new TimelineCollapsed.PlayerFactionResult(loserId, "ERASERS"))));

        then(endGameSaga).should().start(gameId, EndGameTrigger.TIMELINE_COLLAPSED, winnerId, loserId);
    }

    @Test
    void onTimelineStabilized_startsSagaForWinnersAndLosers() {
        var listener = new EndGameSagaEventListener(endGameSaga);
        var gameId = UUID.randomUUID();
        var winnerId = UUID.randomUUID();
        var loserId = UUID.randomUUID();

        listener.onTimelineStabilized(new TimelineStabilized(
                gameId,
                List.of(new TimelineStabilized.PlayerFactionResult(winnerId, "PROPHETS", 2)),
                List.of(new TimelineStabilized.PlayerFactionResult(loserId, "ACTIVISTS", 0))));

        then(endGameSaga).should().start(gameId, EndGameTrigger.TIMELINE_STABILIZED, winnerId, loserId);
    }
}
