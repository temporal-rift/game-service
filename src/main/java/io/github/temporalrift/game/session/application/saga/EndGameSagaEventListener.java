package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import io.github.temporalrift.events.session.TimelineCollapsed;
import io.github.temporalrift.events.session.TimelineStabilized;
import io.github.temporalrift.events.session.WinConditionMet;
import io.github.temporalrift.game.session.domain.saga.EndGameTrigger;

@Component
class EndGameSagaEventListener {

    private final EndGameSaga gameEndSaga;

    EndGameSagaEventListener(EndGameSaga gameEndSaga) {
        this.gameEndSaga = gameEndSaga;
    }

    @ApplicationModuleListener
    void onWinConditionMet(WinConditionMet event) {
        gameEndSaga.start(event.gameId(), EndGameTrigger.WIN_CONDITION_MET, event.winnerId());
    }

    @ApplicationModuleListener
    void onTimelineCollapsed(TimelineCollapsed event) {
        var allIds = Stream.concat(
                        event.winners().stream().map(TimelineCollapsed.PlayerFactionResult::playerId),
                        event.losers().stream().map(TimelineCollapsed.PlayerFactionResult::playerId))
                .toArray(UUID[]::new);
        gameEndSaga.start(event.gameId(), EndGameTrigger.TIMELINE_COLLAPSED, allIds);
    }

    @ApplicationModuleListener
    void onTimelineStabilized(TimelineStabilized event) {
        var allIds = Stream.concat(
                        event.winners().stream().map(TimelineStabilized.PlayerFactionResult::playerId),
                        event.losers().stream().map(TimelineStabilized.PlayerFactionResult::playerId))
                .toArray(UUID[]::new);
        gameEndSaga.start(event.gameId(), EndGameTrigger.TIMELINE_STABILIZED, allIds);
    }
}
