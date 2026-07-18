package io.github.temporalrift.game.scoring.application.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.session.EventsDrawn;
import io.github.temporalrift.game.session.FactionAssigned;
import io.github.temporalrift.game.shared.Faction;

@ExtendWith(MockitoExtension.class)
class ScoringContextProjectionEventListenerTest {

    @Mock
    EraScoringContextRepository contextRepository;

    @InjectMocks
    ScoringContextProjectionEventListener listener;

    @Test
    void onFactionAssigned_upsertsPlayerFaction() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        listener.onFactionAssigned(new FactionAssigned(gameId, playerId, Faction.WEAVERS.name()));

        then(contextRepository).should().upsertPlayerFaction(gameId, playerId, Faction.WEAVERS);
    }

    @Test
    void onFactionAssigned_skipsUnknownFactionWithoutUpserting() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        listener.onFactionAssigned(new FactionAssigned(gameId, playerId, "NOT_A_REAL_FACTION"));

        then(contextRepository).should(never()).upsertPlayerFaction(any(), any(), any());
    }

    @Test
    void onFactionAssigned_skipsNullFactionWithoutUpserting() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        listener.onFactionAssigned(new FactionAssigned(gameId, playerId, null));

        then(contextRepository).should(never()).upsertPlayerFaction(any(), any(), any());
    }

    @Test
    void onEventsDrawn_upsertsExpectedOutcomeCountFromEventListSize() {
        var gameId = UUID.randomUUID();
        var event = new EventsDrawn(
                gameId,
                2,
                List.of(
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "Title 1", List.of(), false),
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "Title 2", List.of(), false),
                        new EventsDrawn.FutureEvent(UUID.randomUUID(), "Title 3", List.of(), false)));

        listener.onEventsDrawn(event);

        then(contextRepository).should().upsertExpectedOutcomeCount(gameId, 2, 3);
    }
}
