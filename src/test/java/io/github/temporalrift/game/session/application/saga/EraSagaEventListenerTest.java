package io.github.temporalrift.game.session.application.saga;

import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.events.action.ActionRoundClosed;
import io.github.temporalrift.events.scoring.ScoresUpdated;
import io.github.temporalrift.events.session.EraStarted;
import io.github.temporalrift.events.shared.Faction;

@ExtendWith(MockitoExtension.class)
class EraSagaEventListenerTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final UUID PLAYER_1 = UUID.randomUUID();
    static final UUID PLAYER_2 = UUID.randomUUID();
    static final List<UUID> PLAYER_IDS = List.of(PLAYER_1, PLAYER_2);

    @Mock
    EraSaga eraSaga;

    @Mock
    EraSagaAdvancer eraSagaAdvancer;

    @InjectMocks
    EraSagaEventListener listener;

    @Test
    @DisplayName("EraStarted — delegates to eraSaga.start with all fields from the event")
    void onEraStarted_delegatesToEraSagaStart() {
        // given
        var cascadedId = UUID.randomUUID();
        var event = new EraStarted(GAME_ID, 2, List.of(cascadedId), PLAYER_IDS);

        // when
        listener.onEraStarted(event);

        // then
        then(eraSaga).should().start(GAME_ID, 2, PLAYER_IDS, List.of(cascadedId));
    }

    @Test
    @DisplayName("ActionRoundClosed — delegates to advancer.handleRoundClosed with gameId from event")
    void onActionRoundClosed_delegatesToAdvancerHandleRoundClosed() {
        // given
        var event = new ActionRoundClosed(GAME_ID, 1, 2, "ALL_SUBMITTED", 4);

        // when
        listener.onActionRoundClosed(event);

        // then
        then(eraSagaAdvancer).should().handleRoundClosed(GAME_ID, event);
    }

    @Test
    @DisplayName("ScoresUpdated — delegates to advancer.handleScoresUpdated with gameId from event")
    void onScoresUpdated_delegatesToAdvancerHandleScoresUpdated() {
        // given
        var updates = List.of(new ScoresUpdated.ScoreUpdate(PLAYER_1, Faction.PROPHETS, 5, "bonus", 15));
        var event = new ScoresUpdated(GAME_ID, 1, updates);

        // when
        listener.onScoresUpdated(event);

        // then
        then(eraSagaAdvancer).should().handleScoresUpdated(GAME_ID, event);
    }
}
