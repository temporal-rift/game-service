package io.github.temporalrift.game.action.domain.saga;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActionRoundSagaState(
        UUID sagaId,
        UUID gameId,
        int eraNumber,
        int roundNumber,
        ActionRoundSagaStatus status,
        List<UUID> pendingPlayerIds,
        Instant timerExpiresAt) {

    public ActionRoundSagaState withStatus(ActionRoundSagaStatus newStatus) {
        return new ActionRoundSagaState(
                sagaId, gameId, eraNumber, roundNumber, newStatus, pendingPlayerIds, timerExpiresAt);
    }

    public ActionRoundSagaState withPendingPlayerIds(List<UUID> updated) {
        return new ActionRoundSagaState(
                sagaId, gameId, eraNumber, roundNumber, status, List.copyOf(updated), timerExpiresAt);
    }
}
