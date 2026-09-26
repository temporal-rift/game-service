package io.github.temporalrift.game.action.domain.actionround;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.CardType;

/** Determines the actions cancelled by the complete same-round NULLIFY correlation. */
public final class RoundCancellation {

    private RoundCancellation() {}

    /**
     * Returns submitted players named by a NULLIFY in the round. A grade II NULLIFY names two players; every entry
     * of its target list cancels. Every NULLIFY is considered simultaneously, so mutually targeted NULLIFY actions
     * both appear in the result.
     */
    public static Set<UUID> cancelledPlayerIds(List<SubmittedAction> submittedActions) {
        var submittedPlayerIds = new LinkedHashSet<UUID>();
        var cancelledPlayerIds = new LinkedHashSet<UUID>();
        for (var action : submittedActions) {
            submittedPlayerIds.add(action.playerId());
            if (action instanceof SubmittedAction.CardAction card && card.cardType() == CardType.NULLIFY) {
                if (card.targetPlayerIds() != null) {
                    cancelledPlayerIds.addAll(card.targetPlayerIds());
                } else if (card.targetPlayerId() != null) {
                    // Legacy single-target rows stored before the player-target list existed.
                    cancelledPlayerIds.add(card.targetPlayerId());
                }
            }
        }
        cancelledPlayerIds.retainAll(submittedPlayerIds);
        return Set.copyOf(cancelledPlayerIds);
    }
}
