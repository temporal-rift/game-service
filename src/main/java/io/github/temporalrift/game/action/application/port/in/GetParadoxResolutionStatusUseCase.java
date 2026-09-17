package io.github.temporalrift.game.action.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Recovers paradox-resolution phase status for the caller's era.
 */
public interface GetParadoxResolutionStatusUseCase {

    Result handle(Query query);

    record Query(UUID gameId, int eraNumber, UUID callerPlayerId) {}

    record Result(
            int eraNumber,
            boolean phaseOpen,
            Integer timerRemainingSeconds,
            int submittedCount,
            int totalPlayers,
            List<UUID> pendingPlayerIds,
            boolean mySubmitted) {}
}
