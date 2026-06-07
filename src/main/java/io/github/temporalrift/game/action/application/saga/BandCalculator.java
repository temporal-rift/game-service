package io.github.temporalrift.game.action.application.saga;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.timeline.BandedProbabilityPublished;
import io.github.temporalrift.events.timeline.BandedProbabilityPublished.ProbabilityBand;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

@Component
class BandCalculator {

    List<BandedProbabilityPublished.EventBandState> computeBands(
            List<SubmittedAction> round1Actions,
            List<SubmittedAction> round2Actions,
            List<FutureEventDefinitionPort.EventDefinition> initialDefinitions) {

        // Build cumulative probabilities: eventId -> (outcomeId -> currentProbability)
        Map<UUID, Map<UUID, Integer>> cumulativeProbabilities = new HashMap<>();
        for (var def : initialDefinitions) {
            var outcomeMap = new HashMap<UUID, Integer>();
            for (var outcome : def.outcomes()) {
                outcomeMap.put(outcome.outcomeId(), outcome.initialProbability());
            }
            cumulativeProbabilities.put(def.eventId(), outcomeMap);
        }

        // Apply round 1 then round 2 actions
        for (var action : round1Actions) {
            applyAction(cumulativeProbabilities, action);
        }
        for (var action : round2Actions) {
            applyAction(cumulativeProbabilities, action);
        }

        // Build result
        List<BandedProbabilityPublished.EventBandState> result = new ArrayList<>();
        for (var def : initialDefinitions) {
            var outcomeMap = cumulativeProbabilities.get(def.eventId());
            if (outcomeMap == null) {
                continue;
            }
            List<BandedProbabilityPublished.OutcomeBandState> outcomeStates = new ArrayList<>();
            for (var outcome : def.outcomes()) {
                var probability = outcomeMap.getOrDefault(outcome.outcomeId(), 0);
                outcomeStates.add(
                        new BandedProbabilityPublished.OutcomeBandState(outcome.outcomeId(), assignBand(probability)));
            }
            result.add(new BandedProbabilityPublished.EventBandState(def.eventId(), outcomeStates));
        }
        return result;
    }

    private void applyAction(Map<UUID, Map<UUID, Integer>> state, SubmittedAction action) {
        switch (action) {
            case SubmittedAction.CardAction card -> applyCardShift(state, card);
            case SubmittedAction.SpecialActionSubmission special -> applySpecialShift(state, special);
        }
    }

    private ProbabilityBand assignBand(int probability) {
        if (probability <= 30) {
            return ProbabilityBand.LOW;
        }
        if (probability <= 60) {
            return ProbabilityBand.MEDIUM;
        }
        return ProbabilityBand.HIGH;
    }

    private void applyCardShift(Map<UUID, Map<UUID, Integer>> state, SubmittedAction.CardAction action) {
        if (action.targetEventId() == null || action.targetOutcomeId() == null) {
            return;
        }
        var outcomeMap = state.get(action.targetEventId());
        if (outcomeMap == null) {
            return;
        }
        if (action.cardType() == CardType.SWING) {
            applySwingShift(outcomeMap, action);
            return;
        }

        var shift =
                switch (action.cardType()) {
                    case PUSH -> 20;
                    case SUPPRESS -> -20;
                    case AMPLIFY -> 0; // Amplify doubles the next card's effect — handled at resolution time
                    case INTERCEPT -> 0;
                    case SCAN -> 0;
                    case TRACE -> 0;
                    case DECOY -> 0;
                    case JAM -> 0;
                    case STALL -> 0;
                    case REDIRECT -> 0;
                    case NULLIFY -> 0;
                    case COLLIDE -> 0;
                    case STABILIZE -> 0;
                    case DETONATE -> 0;
                    case SWING -> 0;
                };
        if (shift != 0) {
            outcomeMap.merge(action.targetOutcomeId(), shift, Integer::sum);
        }
    }

    private void applySwingShift(Map<UUID, Integer> outcomeMap, SubmittedAction.CardAction action) {
        if (action.sourceOutcomeId() == null || action.sourceOutcomeId().equals(action.targetOutcomeId())) {
            return;
        }
        outcomeMap.merge(action.sourceOutcomeId(), -30, Integer::sum);
        outcomeMap.merge(action.targetOutcomeId(), 30, Integer::sum);
    }

    private void applySpecialShift(
            Map<UUID, Map<UUID, Integer>> state, SubmittedAction.SpecialActionSubmission action) {
        if (action.targetEventId() == null || action.targetOutcomeId() == null) {
            return;
        }
        var outcomeMap = state.get(action.targetEventId());
        if (outcomeMap == null) {
            return;
        }
        var shift =
                switch (action.specialAction()) {
                    case ANNIHILATE -> 0; // handled at resolution time
                    case CORRUPT -> 0;
                    case CASCADE -> 0;
                    case FORESIGHT -> 0;
                    case SEAL -> 0;
                    case FULFILLMENT -> 0;
                    case REWRITE -> 0;
                    case MIMIC -> 0;
                    case OBSCURE -> 0;
                    case THREAD -> 0;
                    case TAPESTRY -> 0;
                    case UNRAVEL -> 0;
                    case RALLY -> 0;
                    case EXPOSE -> 0;
                    case MOMENTUM -> 0;
                };
        if (shift != 0) {
            outcomeMap.merge(action.targetOutcomeId(), shift, Integer::sum);
        }
    }

    record BandedOutcome(UUID eventId, UUID outcomeId, ProbabilityBand band) {}
}
