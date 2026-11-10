package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.SpecialActionUnavailableReason;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * Private report, addressed only to {@code playerId}, of which faction specials that player can use in the
 * action round that just opened. It never says who caused an unavailability.
 */
public record SpecialActionAvailabilityReported(
        UUID gameId, int eraNumber, int roundNumber, UUID playerId, List<SpecialAvailability> specials)
        implements ActionEventPayload {

    public SpecialActionAvailabilityReported {
        specials = List.copyOf(specials);
    }

    /** {@code unavailableReason} is present exactly when {@code available} is false. */
    public record SpecialAvailability(
            SpecialAction specialAction, boolean available, SpecialActionUnavailableReason unavailableReason) {}
}
