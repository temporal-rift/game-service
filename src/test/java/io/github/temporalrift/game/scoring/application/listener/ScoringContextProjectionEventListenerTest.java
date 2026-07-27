package io.github.temporalrift.game.scoring.application.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.ActionRoundClosed;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.ForesightDeclared;
import io.github.temporalrift.game.shared.OutcomeAnnihilated;

@ExtendWith(MockitoExtension.class)
class ScoringContextProjectionEventListenerTest {

    @Mock
    EraScoringContextRepository contextRepository;

    @Mock
    EraScoringCompletionChecker completionChecker;

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

    @Test
    void onEventsDrawn_upsertsPerEventOutcomeBaseline() {
        var gameId = UUID.randomUUID();
        var eventId1 = UUID.randomUUID();
        var eventId2 = UUID.randomUUID();
        var event = new EventsDrawn(
                gameId,
                2,
                List.of(
                        new EventsDrawn.FutureEvent(
                                eventId1,
                                "Title 1",
                                List.of(
                                        new EventsDrawn.Outcome(UUID.randomUUID(), "A", 33),
                                        new EventsDrawn.Outcome(UUID.randomUUID(), "B", 33),
                                        new EventsDrawn.Outcome(UUID.randomUUID(), "C", 34)),
                                false),
                        new EventsDrawn.FutureEvent(
                                eventId2,
                                "Title 2",
                                List.of(
                                        new EventsDrawn.Outcome(UUID.randomUUID(), "A", 50),
                                        new EventsDrawn.Outcome(UUID.randomUUID(), "B", 50)),
                                false)));

        listener.onEventsDrawn(event);

        then(contextRepository).should().upsertEventOutcomeBaseline(gameId, 2, eventId1, 3);
        then(contextRepository).should().upsertEventOutcomeBaseline(gameId, 2, eventId2, 2);
    }

    @Test
    void onForesightDeclared_upsertsWrittenOutcome() {
        var gameId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        listener.onForesightDeclared(new ForesightDeclared(gameId, 2, eventId, outcomeId, playerId));

        then(contextRepository).should().upsertWrittenOutcome(gameId, 2, eventId, outcomeId, playerId);
    }

    @Test
    void onOutcomeAnnihilated_recordsAnnihilatedOutcome() {
        var gameId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();
        var playerId = UUID.randomUUID();

        listener.onOutcomeAnnihilated(new OutcomeAnnihilated(gameId, 2, eventId, outcomeId, playerId));

        then(contextRepository).should().recordAnnihilatedOutcome(gameId, 2, eventId, outcomeId, playerId);
    }

    @Test
    void onActionRoundClosed_finalRound_marksActionFactsReadyAndTriesCompletion() {
        var gameId = UUID.randomUUID();

        listener.onActionRoundClosed(new ActionRoundClosed(gameId, 2, 3, "ALL_SUBMITTED", 3));

        then(contextRepository).should().markActionFactsReady(gameId, 2);
        then(completionChecker).should().tryComplete(gameId, 2);
    }

    @Test
    void onActionRoundClosed_nonFinalRound_doesNothing() {
        var gameId = UUID.randomUUID();

        listener.onActionRoundClosed(new ActionRoundClosed(gameId, 2, 1, "ALL_SUBMITTED", 3));

        then(contextRepository).should(never()).markActionFactsReady(any(), anyInt());
        then(completionChecker).should(never()).tryComplete(any(), anyInt());
    }
}
