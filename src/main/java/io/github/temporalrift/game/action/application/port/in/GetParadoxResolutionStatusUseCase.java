package io.github.temporalrift.game.action.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Reads participant-scoped status for an era's paradox-resolution phase: whether reactive offers
 * are open, the authoritative deadline, aggregate submission progress, and the caller's own
 * submission flag.
 */
public interface GetParadoxResolutionStatusUseCase {

    Result handle(Query query);

    record Query(UUID gameId, int eraNumber, UUID callerPlayerId) {}

    record Result(
            int eraNumber,
            boolean phaseOpen,
            int timerRemainingSeconds,
            int submittedCount,
            int totalPlayers,
            List<UUID> pendingPlayerIds,
            boolean mySubmitted) {}
}
