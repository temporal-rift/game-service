package io.github.temporalrift.game.action.domain.actionround;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A round's pending players and any actions already submitted when it is created. */
public record ActionRoundParticipants(List<UUID> pendingPlayerIds, List<SubmittedAction> submittedActions) {

    public ActionRoundParticipants {
        pendingPlayerIds = List.copyOf(Objects.requireNonNull(pendingPlayerIds, "pendingPlayerIds must not be null"));
        submittedActions = List.copyOf(Objects.requireNonNull(submittedActions, "submittedActions must not be null"));
    }

    public static ActionRoundParticipants pending(List<UUID> pendingPlayerIds) {
        return new ActionRoundParticipants(pendingPlayerIds, List.of());
    }
}
