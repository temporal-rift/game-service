package io.github.temporalrift.game.scoring.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.scoring.domain.context.ActionScoringFact;
import io.github.temporalrift.game.scoring.domain.context.AnnihilationFact;
import io.github.temporalrift.game.scoring.domain.context.ChainScoringFact;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.context.FulfillmentDeclarationFact;
import io.github.temporalrift.game.scoring.domain.context.PlayerFaction;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;
import io.github.temporalrift.game.shared.Faction;

@DisplayName("EraScoreEvaluator")
class EraScoreEvaluatorTest {

    static final UUID GAME_ID = UUID.randomUUID();
    static final int ERA = 1;

    final EraScoreEvaluator evaluator = new EraScoreEvaluator();

    @Test
    @DisplayName("prophet receives EVENT_RESOLVED_AS_WRITTEN when written outcome wins")
    void prophetWrittenOutcomeWins() {
        var prophetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, writtenOutcomeId, writtenOutcomeId, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, writtenOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).playerId()).isEqualTo(prophetId);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.EVENT_RESOLVED_AS_WRITTEN);
        assertThat(decisions.get(0).eraNumber()).isEqualTo(ERA);
    }

    @Test
    @DisplayName("prophet receives EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN when winning outcome differs")
    void prophetWrittenOutcomeLoses() {
        var prophetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();
        var otherOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, otherOutcomeId, writtenOutcomeId, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, otherOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN);
    }

    @Test
    @DisplayName("prophet skips events with no written outcome")
    void prophetSkipsEventsWithNoWrittenOutcome() {
        var prophetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, winningOutcomeId, null, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, winningOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("eraser receives ERA_ENDED_WITH_FEWER_OUTCOMES when any event lost outcomes")
    void eraserEraEndedWithFewerOutcomes() {
        var eraserId = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(eraserId, Faction.ERASERS)),
                List.of(new EventOutcomeFact(eventId, UUID.randomUUID(), null, 3, 2)),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.ERA_ENDED_WITH_FEWER_OUTCOMES);
        assertThat(decisions.get(0).eraNumber()).isEqualTo(ERA);
    }

    @Test
    @DisplayName("eraser receives no decision when no event lost outcomes")
    void eraserNoFewerOutcomes() {
        var eraserId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(eraserId, Faction.ERASERS)),
                List.of(new EventOutcomeFact(UUID.randomUUID(), UUID.randomUUID(), null, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("weaver receives CHAIN_COMPLETED from chain facts")
    void weaverChainCompleted() {
        var weaverId = UUID.randomUUID();
        var chainId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(weaverId, Faction.WEAVERS)),
                List.of(),
                List.of(),
                List.of(new ChainScoringFact(weaverId, chainId, ScoreReason.CHAIN_COMPLETED, ERA)),
                List.of(),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.CHAIN_COMPLETED);
    }

    @Test
    @DisplayName("weaver receives CHAIN_BROKEN from chain facts")
    void weaverChainBroken() {
        var weaverId = UUID.randomUUID();
        var chainId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(weaverId, Faction.WEAVERS)),
                List.of(),
                List.of(),
                List.of(new ChainScoringFact(weaverId, chainId, ScoreReason.CHAIN_BROKEN, ERA)),
                List.of(),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.CHAIN_BROKEN);
    }

    @Test
    @DisplayName("weaver chain-fact decision is stamped with the fact's own era, not the scoring pass's era")
    void weaverChainDecisionUsesFactsOwnEra() {
        var weaverId = UUID.randomUUID();
        var chainId = UUID.randomUUID();
        var scoringPassEra = 3;
        var factOwnEra = 2;

        var context = new EraScoringContext(
                GAME_ID,
                scoringPassEra,
                List.of(new PlayerFaction(weaverId, Faction.WEAVERS)),
                List.of(),
                List.of(),
                List.of(new ChainScoringFact(weaverId, chainId, ScoreReason.CHAIN_COMPLETED, factOwnEra)),
                List.of(),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).eraNumber()).isEqualTo(factOwnEra);
    }

    @Test
    @DisplayName("activist receives scoring reason from action facts")
    void activistActionFact() {
        var activistId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(activistId, Faction.ACTIVISTS)),
                List.of(),
                List.of(new ActionScoringFact(activistId, Faction.ACTIVISTS, ScoreReason.DECLARED_OUTCOME_WON)),
                List.of(),
                List.of(),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.DECLARED_OUTCOME_WON);
        assertThat(decisions.get(0).eraNumber()).isEqualTo(ERA);
    }

    @Test
    @DisplayName("decisions are sorted by player id then reason name")
    void decisionsAreSorted() {
        var id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var id2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var chainId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(id2, Faction.WEAVERS), new PlayerFaction(id1, Faction.WEAVERS)),
                List.of(),
                List.of(),
                List.of(
                        new ChainScoringFact(id2, chainId, ScoreReason.CHAIN_COMPLETED, ERA),
                        new ChainScoringFact(id1, chainId, ScoreReason.CHAIN_LINK_ADDED, ERA)),
                List.of(),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).hasSize(2);
        assertThat(decisions.get(0).playerId()).isEqualTo(id1);
        assertThat(decisions.get(1).playerId()).isEqualTo(id2);
    }

    @Test
    @DisplayName("prophet receives FULFILLMENT_SUCCEEDED instead of EVENT_RESOLVED_AS_WRITTEN when declared and event "
            + "resolves as written")
    void prophetFulfillmentDeclaredAndSucceeds() {
        var prophetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, writtenOutcomeId, writtenOutcomeId, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FulfillmentDeclarationFact(prophetId, eventId)));

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, writtenOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).playerId()).isEqualTo(prophetId);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.FULFILLMENT_SUCCEEDED);
    }

    @Test
    @DisplayName("prophet receives EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN, not a Fulfillment bonus, when declared "
            + "and event resolves differently")
    void prophetFulfillmentDeclaredAndFails() {
        var prophetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();
        var otherOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, otherOutcomeId, writtenOutcomeId, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FulfillmentDeclarationFact(prophetId, eventId)));

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, otherOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN);
    }

    @Test
    @DisplayName("prophet Fulfillment declared on an event with no written outcome produces no credit")
    void prophetFulfillmentDeclaredOnUnwrittenEvent() {
        var prophetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var winningOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, winningOutcomeId, null, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FulfillmentDeclarationFact(prophetId, eventId)));

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, winningOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName("prophet Fulfillment declared on a cascaded event (no OutcomeApplied) produces no credit")
    void prophetFulfillmentDeclaredOnCascadedEvent() {
        var prophetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, null, writtenOutcomeId, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FulfillmentDeclarationFact(prophetId, eventId)));

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).isEmpty();
    }

    @Test
    @DisplayName(
            "prophet receives two separate FULFILLMENT_SUCCEEDED credits for declarations on two different " + "events")
    void prophetFulfillmentDeclaredOnTwoEvents() {
        var prophetId = UUID.randomUUID();
        var eventId1 = UUID.randomUUID();
        var eventId2 = UUID.randomUUID();
        var outcomeId1 = UUID.randomUUID();
        var outcomeId2 = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(prophetId, Faction.PROPHETS)),
                List.of(
                        new EventOutcomeFact(eventId1, outcomeId1, outcomeId1, 3, 3),
                        new EventOutcomeFact(eventId2, outcomeId2, outcomeId2, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new FulfillmentDeclarationFact(prophetId, eventId1),
                        new FulfillmentDeclarationFact(prophetId, eventId2)));

        var outcomes = List.of(
                new OutcomeApplied(GAME_ID, ERA, eventId1, outcomeId1, List.of()),
                new OutcomeApplied(GAME_ID, ERA, eventId2, outcomeId2, List.of()));

        var decisions = evaluator.evaluate(context, outcomes);

        assertThat(decisions).hasSize(2);
        assertThat(decisions).allMatch(d -> d.reason() == ScoreReason.FULFILLMENT_SUCCEEDED);
    }

    @Test
    @DisplayName("two different Prophets each declaring Fulfillment on the same event each receive their own credit")
    void twoProphetsDeclareFulfillmentOnSameEvent() {
        var prophetId1 = UUID.randomUUID();
        var prophetId2 = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(
                        new PlayerFaction(prophetId1, Faction.PROPHETS),
                        new PlayerFaction(prophetId2, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, writtenOutcomeId, writtenOutcomeId, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new FulfillmentDeclarationFact(prophetId1, eventId),
                        new FulfillmentDeclarationFact(prophetId2, eventId)));

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, writtenOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).hasSize(2);
        assertThat(decisions).allMatch(d -> d.reason() == ScoreReason.FULFILLMENT_SUCCEEDED);
        assertThat(decisions).extracting("playerId").containsExactlyInAnyOrder(prophetId1, prophetId2);
    }

    @Test
    @DisplayName("Fulfillment declared on an event written by a different Prophet still resolves normally")
    void prophetFulfillmentDeclaredOnEventWrittenByAnotherProphet() {
        var declaringProphetId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var writtenOutcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(declaringProphetId, Faction.PROPHETS)),
                List.of(new EventOutcomeFact(eventId, writtenOutcomeId, writtenOutcomeId, 3, 3)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FulfillmentDeclarationFact(declaringProphetId, eventId)));

        var outcome = new OutcomeApplied(GAME_ID, ERA, eventId, writtenOutcomeId, List.of());

        var decisions = evaluator.evaluate(context, List.of(outcome));

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).reason()).isEqualTo(ScoreReason.FULFILLMENT_SUCCEEDED);
    }

    @Test
    @DisplayName("eraser receives ANNIHILATED_OUTCOME for each annihilation attributed to them")
    void eraserReceivesAnnihilatedOutcome() {
        var eraserId = UUID.randomUUID();
        var eventId = UUID.randomUUID();
        var outcomeId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(eraserId, Faction.ERASERS)),
                List.of(new EventOutcomeFact(eventId, UUID.randomUUID(), null, 3, 2)),
                List.of(),
                List.of(),
                List.of(new AnnihilationFact(eventId, outcomeId, eraserId)),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions).hasSize(2);
        assertThat(decisions).anyMatch(d -> d.reason() == ScoreReason.ANNIHILATED_OUTCOME);
        assertThat(decisions).anyMatch(d -> d.reason() == ScoreReason.ERA_ENDED_WITH_FEWER_OUTCOMES);
    }

    @Test
    @DisplayName("eraser receives two separate ANNIHILATED_OUTCOME credits for annihilations on two different events")
    void eraserReceivesTwoAnnihilatedOutcomeCredits() {
        var eraserId = UUID.randomUUID();
        var eventId1 = UUID.randomUUID();
        var eventId2 = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(eraserId, Faction.ERASERS)),
                List.of(
                        new EventOutcomeFact(eventId1, UUID.randomUUID(), null, 3, 2),
                        new EventOutcomeFact(eventId2, UUID.randomUUID(), null, 3, 2)),
                List.of(),
                List.of(),
                List.of(
                        new AnnihilationFact(eventId1, UUID.randomUUID(), eraserId),
                        new AnnihilationFact(eventId2, UUID.randomUUID(), eraserId)),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions.stream()
                        .filter(d -> d.reason() == ScoreReason.ANNIHILATED_OUTCOME)
                        .count())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("two different Erasers each annihilating receive their own ANNIHILATED_OUTCOME credit")
    void twoErasersEachAnnihilate() {
        var eraserId1 = UUID.randomUUID();
        var eraserId2 = UUID.randomUUID();
        var eventId = UUID.randomUUID();

        var context = new EraScoringContext(
                GAME_ID,
                ERA,
                List.of(new PlayerFaction(eraserId1, Faction.ERASERS), new PlayerFaction(eraserId2, Faction.ERASERS)),
                List.of(new EventOutcomeFact(eventId, UUID.randomUUID(), null, 3, 1)),
                List.of(),
                List.of(),
                List.of(
                        new AnnihilationFact(eventId, UUID.randomUUID(), eraserId1),
                        new AnnihilationFact(eventId, UUID.randomUUID(), eraserId2)),
                List.of());

        var decisions = evaluator.evaluate(context, List.of());

        assertThat(decisions.stream()
                        .filter(d -> d.reason() == ScoreReason.ANNIHILATED_OUTCOME)
                        .map(PlayerScoreDecision::playerId))
                .containsExactlyInAnyOrder(eraserId1, eraserId2);
    }
}
