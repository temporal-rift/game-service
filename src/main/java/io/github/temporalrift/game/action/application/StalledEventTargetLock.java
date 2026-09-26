package io.github.temporalrift.game.action.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.action.domain.actionround.InvalidActionTargetException;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.shared.domain.model.CardType;

/**
 * Resolves which events are stalled for targeting purposes from durable prior-round submissions in the
 * same game and era. A {@code STALL} card stalled its event unless a {@code NULLIFY} cancelled the
 * staller's action in that same round; cancellation follows the round-resolution rule where every
 * {@code NULLIFY} contributes its named targets simultaneously, including mutually-targeting nullifies.
 */
@Component
public class StalledEventTargetLock {

    private final ActionRoundRepository actionRoundRepository;

    public StalledEventTargetLock(ActionRoundRepository actionRoundRepository) {
        this.actionRoundRepository = actionRoundRepository;
    }

    /**
     * Returns the events stalled by uncancelled {@code STALL} submissions in rounds before
     * {@code roundNumber} of the same game and era.
     */
    public Set<UUID> stalledEventIds(UUID gameId, int eraNumber, int roundNumber) {
        var stalled = new HashSet<UUID>();
        for (int priorRound = 1; priorRound < roundNumber; priorRound++) {
            var round = actionRoundRepository
                    .findByGameIdAndEraNumberAndRoundNumber(gameId, eraNumber, priorRound)
                    .orElse(null);
            if (round == null) {
                continue;
            }
            stalled.addAll(uncancelledStalls(round.submittedActions()));
        }
        return Set.copyOf(stalled);
    }

    /** Throws unless {@code targetEventId} is null or not stalled for the rest of the era. */
    public void requireEventNotStalled(UUID gameId, int eraNumber, int roundNumber, UUID targetEventId) {
        if (targetEventId == null || roundNumber <= 1) {
            return;
        }
        if (stalledEventIds(gameId, eraNumber, roundNumber).contains(targetEventId)) {
            throw InvalidActionTargetException.stalledEventTarget(targetEventId);
        }
    }

    private static Set<UUID> uncancelledStalls(java.util.List<SubmittedAction> submittedActions) {
        var byPlayer = new HashMap<UUID, SubmittedAction>();
        for (var action : submittedActions) {
            byPlayer.putIfAbsent(action.playerId(), action);
        }
        var cancelledPlayerIds = new HashSet<UUID>();
        for (var action : submittedActions) {
            if (action instanceof SubmittedAction.CardAction card
                    && card.cardType() == CardType.NULLIFY
                    && card.targetPlayerIds() != null) {
                for (var targetPlayerId : card.targetPlayerIds()) {
                    if (byPlayer.containsKey(targetPlayerId)) {
                        cancelledPlayerIds.add(targetPlayerId);
                    }
                }
            }
        }
        var stalled = new HashSet<UUID>();
        for (var action : submittedActions) {
            if (action instanceof SubmittedAction.CardAction card
                    && card.cardType() == CardType.STALL
                    && card.targetEventId() != null
                    && !cancelledPlayerIds.contains(card.playerId())) {
                stalled.add(card.targetEventId());
            }
        }
        return stalled;
    }
}
