package io.github.temporalrift.game.scoring.application.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import io.github.temporalrift.game.scoring.application.command.EraScoringCompletionChecker;
import io.github.temporalrift.game.scoring.domain.port.out.EraScoringContextRepository;
import io.github.temporalrift.game.shared.ActivistDeclarationRecorded;
import io.github.temporalrift.game.shared.ActivistDeclarationResolved;
import io.github.temporalrift.game.shared.EraActionFactsFinalized;
import io.github.temporalrift.game.shared.EventsDrawn;
import io.github.temporalrift.game.shared.ExposeBehaviorChanged;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.FactionAssigned;
import io.github.temporalrift.game.shared.ForesightDeclared;
import io.github.temporalrift.game.shared.OutcomeAnnihilated;
import io.github.temporalrift.game.shared.SpecialAction;

@ExtendWith(MockitoExtension.class)
class ScoringContextProjectionEventListenerTest {

    @Mock
    EraScoringContextRepository contextRepository;

    @Mock
    EraScoringCompletionChecker completionChecker;

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

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
    void onActivistDeclarationRecorded_projectsThenPublishesOnlyDurableResolution() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var declaration = new ActivistDeclarationRecorded(
                gameId, 2, 1, playerId, SpecialAction.RALLY, UUID.randomUUID(), UUID.randomUUID());
        var resolution = new ActivistDeclarationResolved(gameId, 2, playerId, true);
        given(contextRepository.resolveActivistDeclarations(gameId, 2)).willReturn(List.of(resolution));

        listener.onActivistDeclarationRecorded(declaration);

        then(contextRepository).should().upsertActivistDeclaration(declaration);
        then(applicationEventPublisher).should().publishEvent(resolution);
    }

    @Test
    void onExposeBehaviorChanged_recordsExactlyTheActivistExposeFact() {
        var gameId = UUID.randomUUID();
        var activistPlayerId = UUID.randomUUID();

        listener.onExposeBehaviorChanged(new ExposeBehaviorChanged(gameId, 2, activistPlayerId, UUID.randomUUID()));

        then(contextRepository)
                .should()
                .recordActionFact(
                        gameId,
                        2,
                        activistPlayerId,
                        Faction.ACTIVISTS,
                        io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason
                                .EXPOSE_CHANGED_PLAYER_BEHAVIOR);
    }

    @Test
    void onEraActionFactsFinalized_appliesForesightAndAnnihilationFactsThenMarksReadyAndTriesCompletion() {
        var gameId = UUID.randomUUID();
        var foresightEventId = UUID.randomUUID();
        var foresightOutcomeId = UUID.randomUUID();
        var foresightPlayerId = UUID.randomUUID();
        var annihilatedEventId = UUID.randomUUID();
        var annihilatedOutcomeId = UUID.randomUUID();
        var annihilatingPlayerId = UUID.randomUUID();
        var activistPlayerId = UUID.randomUUID();
        var activistEventId = UUID.randomUUID();
        var activistOutcomeId = UUID.randomUUID();
        var revisionistPlayerId = UUID.randomUUID();
        var revisionistEventId = UUID.randomUUID();
        var revisionistOutcomeId = UUID.randomUUID();
        var event = new EraActionFactsFinalized(
                gameId,
                2,
                List.of(new EraActionFactsFinalized.ForesightFact(
                        foresightEventId, foresightOutcomeId, foresightPlayerId)),
                List.of(new EraActionFactsFinalized.AnnihilationFact(
                        annihilatedEventId, annihilatedOutcomeId, annihilatingPlayerId)),
                List.of(),
                List.of(new EraActionFactsFinalized.ActivistDeclarationFact(
                        activistPlayerId, SpecialAction.RALLY, activistEventId, activistOutcomeId)),
                List.of(new EraActionFactsFinalized.RevisionistFact(
                        revisionistPlayerId, SpecialAction.REWRITE, revisionistEventId, revisionistOutcomeId)));

        listener.onEraActionFactsFinalized(event);

        then(contextRepository)
                .should()
                .upsertWrittenOutcome(gameId, 2, foresightEventId, foresightOutcomeId, foresightPlayerId);
        then(contextRepository)
                .should()
                .recordAnnihilatedOutcome(gameId, 2, annihilatedEventId, annihilatedOutcomeId, annihilatingPlayerId);
        then(contextRepository)
                .should()
                .upsertActivistDeclaration(new ActivistDeclarationRecorded(
                        gameId, 2, 1, activistPlayerId, SpecialAction.RALLY, activistEventId, activistOutcomeId));
        then(contextRepository)
                .should()
                .recordRevisionistAction(
                        gameId,
                        2,
                        revisionistPlayerId,
                        SpecialAction.REWRITE,
                        revisionistEventId,
                        revisionistOutcomeId);
        then(contextRepository).should().resolveRevisionistActions(gameId, 2);
        then(contextRepository).should().markActionFactsReady(gameId, 2);
        then(completionChecker).should().tryComplete(gameId, 2);
    }

    @Test
    void onEraActionFactsFinalized_appliesFulfillmentFacts() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var event = new EraActionFactsFinalized(
                gameId,
                2,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new EraActionFactsFinalized.FulfillmentFact(playerId, targetEventId)));

        listener.onEraActionFactsFinalized(event);

        then(contextRepository).should().recordFulfillmentDeclaration(gameId, 2, playerId, targetEventId);
    }

    @Test
    void onEraActionFactsFinalized_appliesCorruptCorrelationFacts() {
        var gameId = UUID.randomUUID();
        var corruptingPlayerId = UUID.randomUUID();
        var targetPlayerId = UUID.randomUUID();
        var cardInstanceId = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var sourceOutcomeId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        var event = new EraActionFactsFinalized(
                gameId,
                2,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new EraActionFactsFinalized.CorruptCorrelationFact(
                        corruptingPlayerId,
                        targetPlayerId,
                        cardInstanceId,
                        targetEventId,
                        sourceOutcomeId,
                        targetOutcomeId)));

        listener.onEraActionFactsFinalized(event);

        then(contextRepository)
                .should()
                .recordCorruptCorrelation(
                        gameId,
                        2,
                        corruptingPlayerId,
                        targetPlayerId,
                        cardInstanceId,
                        targetEventId,
                        sourceOutcomeId,
                        targetOutcomeId);
    }

    @Test
    void onEraActionFactsFinalized_emptyFactLists_stillMarksReadyAndTriesCompletion() {
        var gameId = UUID.randomUUID();

        listener.onEraActionFactsFinalized(new EraActionFactsFinalized(gameId, 2, List.of(), List.of()));

        then(contextRepository).should(never()).upsertWrittenOutcome(any(), anyInt(), any(), any(), any());
        then(contextRepository).should(never()).recordAnnihilatedOutcome(any(), anyInt(), any(), any(), any());
        then(contextRepository).should().markActionFactsReady(gameId, 2);
        then(completionChecker).should().tryComplete(gameId, 2);
    }
}
