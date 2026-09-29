package io.github.temporalrift.game.action.application.saga;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * Resolves a round's Traces against the round before it. Both the private reveals and the era's traced-Mimic
 * facts come from here, so no player is exposed by a result that differs from what the tracer received.
 */
final class TraceResolver {

    private static final int FINAL_ROUND_NUMBER = 3;

    private final ActionRoundRepository actionRoundRepository;
    private final FutureEventDefinitionPort futureEventDefinitionPort;

    TraceResolver(ActionRoundRepository actionRoundRepository, FutureEventDefinitionPort futureEventDefinitionPort) {
        this.actionRoundRepository = actionRoundRepository;
        this.futureEventDefinitionPort = futureEventDefinitionPort;
    }

    record TraceResult(
            UUID tracerPlayerId,
            UUID targetEventId,
            List<UUID> influencerPlayerIds,
            List<UUID> mimicInfluencerPlayerIds) {}

    List<TraceResult> resolve(ActionRound traceRound) {
        var traces = RoundActions.live(traceRound).stream()
                .filter(SubmittedAction.CardAction.class::isInstance)
                .map(SubmittedAction.CardAction.class::cast)
                .filter(card -> card.cardType() == CardType.TRACE)
                .toList();
        if (traces.isEmpty()) {
            return List.of();
        }
        var observedRound = previousRound(traceRound);
        var observedActions = observedRound.map(RoundActions::live).orElseGet(List::of);
        var obscuredPlayerIds = observedRound.map(this::obscuredDuring).orElseGet(Set::of);
        var results = new ArrayList<TraceResult>();
        for (var trace : traces) {
            for (var targetEventId : traceTargets(trace, observedRound)) {
                var influencers = new LinkedHashSet<UUID>();
                var mimicInfluencers = new LinkedHashSet<UUID>();
                observedActions.stream()
                        .filter(action -> !obscuredPlayerIds.contains(action.playerId()))
                        .filter(action -> action.isTracedInfluenceOn(targetEventId))
                        .forEach(action -> {
                            influencers.add(action.playerId());
                            if (isMimic(action)) {
                                mimicInfluencers.add(action.playerId());
                            }
                        });
                results.add(new TraceResult(
                        trace.playerId(), targetEventId, List.copyOf(influencers), List.copyOf(mimicInfluencers)));
            }
        }
        return results;
    }

    private static boolean isMimic(SubmittedAction action) {
        return action instanceof SubmittedAction.SpecialActionSubmission special
                && special.specialAction() == SpecialAction.MIMIC;
    }

    private Optional<ActionRound> previousRound(ActionRound round) {
        var eraNumber = round.eraNumber();
        var roundNumber = round.roundNumber();
        if (eraNumber == 1 && roundNumber == 1) {
            return Optional.empty();
        }
        var predecessorEra = roundNumber == 1 ? eraNumber - 1 : eraNumber;
        var predecessorRound = roundNumber == 1 ? FINAL_ROUND_NUMBER : roundNumber - 1;
        return actionRoundRepository.findByGameIdAndEraNumberAndRoundNumber(
                round.gameId(), predecessorEra, predecessorRound);
    }

    // The Obscure flag of a past round has already been cleared, so coverage is derived from the round before it.
    private Set<UUID> obscuredDuring(ActionRound observedRound) {
        if (observedRound.roundNumber() == 1) {
            return Set.of();
        }
        return actionRoundRepository
                .findByGameIdAndEraNumberAndRoundNumber(
                        observedRound.gameId(), observedRound.eraNumber(), observedRound.roundNumber() - 1)
                .map(RoundActions::live)
                .map(RoundActions::obscureSubmitters)
                .orElseGet(Set::of);
    }

    private List<UUID> traceTargets(SubmittedAction.CardAction trace, Optional<ActionRound> observedRound) {
        return switch (trace.grade()) {
            case I -> List.of(trace.targetEventId());
            case II ->
                observedRound
                        .map(observed ->
                                futureEventDefinitionPort
                                        .findByGameIdAndEraNumber(observed.gameId(), observed.eraNumber())
                                        .stream()
                                        .map(FutureEventDefinitionPort.EventDefinition::eventId)
                                        .toList())
                        .orElseGet(List::of);
            // Submission validation rejects unsupported TRACE grades; yielding no targets here
            // keeps a stray record from rolling back the round close.
            case III -> List.of();
        };
    }
}
