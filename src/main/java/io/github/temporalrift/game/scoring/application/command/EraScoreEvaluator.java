package io.github.temporalrift.game.scoring.application.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.timeline.OutcomeApplied;
import io.github.temporalrift.game.scoring.domain.context.EraScoringContext;
import io.github.temporalrift.game.scoring.domain.context.EventOutcomeFact;
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
                        var reason = fact.writtenOutcomeId().equals(outcome.winningOutcomeId())
                                ? ScoreReason.EVENT_RESOLVED_AS_WRITTEN
                                : ScoreReason.EVENT_RESOLVED_DIFFERENTLY_THAN_WRITTEN;
                        decisions.add(new PlayerScoreDecision(playerId, reason));
                    });
        }
        return decisions;
    }

    private List<PlayerScoreDecision> eraserDecisions(UUID playerId, EraScoringContext context) {
        boolean anyFewer = context.eventOutcomes().stream()
                .anyMatch(fact -> fact.endingOutcomeCount() < fact.startingOutcomeCount());
        if (anyFewer) {
            return List.of(new PlayerScoreDecision(playerId, ScoreReason.ERA_ENDED_WITH_FEWER_OUTCOMES));
        }
        return List.of();
    }

    private List<PlayerScoreDecision> weaverDecisions(UUID playerId, EraScoringContext context) {
        return context.chainFacts().stream()
                .filter(fact -> fact.playerId().equals(playerId))
                .map(fact -> new PlayerScoreDecision(playerId, fact.reason()))
                .toList();
    }

    private List<PlayerScoreDecision> actionDecisions(UUID playerId, EraScoringContext context) {
        return context.actionFacts().stream()
                .filter(fact -> fact.playerId().equals(playerId))
                .map(fact -> new PlayerScoreDecision(playerId, fact.reason()))
                .toList();
    }

    private java.util.Optional<EventOutcomeFact> findEventFact(EraScoringContext context, UUID eventId) {
        return context.eventOutcomes().stream()
                .filter(fact -> fact.eventId().equals(eventId))
                .findFirst();
    }
}
