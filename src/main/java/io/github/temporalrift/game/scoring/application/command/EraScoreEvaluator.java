package io.github.temporalrift.game.scoring.application.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
import io.github.temporalrift.game.scoring.domain.event.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.playerscore.ScoreReason;

@Component
class EraScoreEvaluator {

    List<PlayerScoreDecision> evaluate(EraScoringContext context, List<OutcomeApplied> outcomes) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(outcomes, "outcomes must not be null");

        var decisions = new ArrayList<PlayerScoreDecision>();

        for (var player : context.players()) {
            switch (player.faction()) {
                case PROPHETS -> decisions.addAll(prophetDecisions(player.playerId(), context, outcomes));
                case ERASERS -> decisions.addAll(eraserDecisions(player.playerId(), context));
                case WEAVERS -> decisions.addAll(weaverDecisions(player.playerId(), context));
                case ACTIVISTS, REVISIONISTS -> decisions.addAll(actionDecisions(player.playerId(), context));
            }
        }

        decisions.sort(Comparator.comparing(PlayerScoreDecision::playerId)
                .thenComparing(d -> d.reason().name()));

        return decisions;
    }

    private List<PlayerScoreDecision> prophetDecisions(
            UUID playerId, EraScoringContext context, List<OutcomeApplied> outcomes) {
        var decisions = new ArrayList<PlayerScoreDecision>();
        for (var outcome : outcomes) {
            findEventFact(context, outcome.eventId())
                    .filter(fact -> fact.writtenOutcomeId() != null)
                    .ifPresent(fact -> {
                        boolean resolvedAsWritten = fact.writtenOutcomeId().equals(outcome.winningOutcomeId());
                        boolean declaredFulfillment = context.fulfillmentDeclarations().stream()
                                .anyMatch(declaration -> declaration.playerId().equals(playerId)
                                        && declaration.targetEventId().equals(outcome.eventId()));
                        var reason = resolvedAsWritten
                                ? (declaredFulfillment
                                        ? ScoreReason.FULFILLMENT_SUCCEEDED
                                        : ScoreReason.EVENT_RESOLVED_AS_WRITTEN)
                                : ScoreReason.EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN;
                        decisions.add(new PlayerScoreDecision(playerId, reason, context.eraNumber()));
                    });
        }
        return decisions;
    }

    // CORRUPTED_OPPONENT_CARD is deliberately not emitted here: scoring_context_corrupt_correlation only
    // records that a Corrupt was blind-targeted and matched to a card at round close, not that its
    // inversion actually took effect (a Seal can void it). That confirmation is a timeline-service
    // resolution-time fact that does not exist yet — see temporal-rift/timeline-service#12 and #16.
    private List<PlayerScoreDecision> eraserDecisions(UUID playerId, EraScoringContext context) {
        var decisions = new ArrayList<PlayerScoreDecision>();

        context.annihilationFacts().stream()
                .filter(fact -> fact.playerId().equals(playerId))
                .forEach(fact -> decisions.add(
                        new PlayerScoreDecision(playerId, ScoreReason.ANNIHILATED_OUTCOME, context.eraNumber())));

        boolean anyFewer = context.eventOutcomes().stream()
                .anyMatch(fact -> fact.endingOutcomeCount() < fact.startingOutcomeCount());
        if (anyFewer) {
            decisions.add(
                    new PlayerScoreDecision(playerId, ScoreReason.ERA_ENDED_WITH_FEWER_OUTCOMES, context.eraNumber()));
        }

        return decisions;
    }

    private List<PlayerScoreDecision> weaverDecisions(UUID playerId, EraScoringContext context) {
        return context.chainFacts().stream()
                .filter(fact -> fact.playerId().equals(playerId))
                .map(fact -> new PlayerScoreDecision(playerId, fact.reason(), fact.eraNumber()))
                .toList();
    }

    private List<PlayerScoreDecision> actionDecisions(UUID playerId, EraScoringContext context) {
        return context.actionFacts().stream()
                .filter(fact -> fact.playerId().equals(playerId))
                .map(fact -> new PlayerScoreDecision(playerId, fact.reason(), context.eraNumber()))
                .toList();
    }

    private Optional<EventOutcomeFact> findEventFact(EraScoringContext context, UUID eventId) {
        return context.eventOutcomes().stream()
                .filter(fact -> fact.eventId().equals(eventId))
                .findFirst();
    }
}
