package io.github.temporalrift.game.action.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Reads public submission status for a normal action round.
 */
public interface GetRoundStatusUseCase {

    Result handle(Query query);

    record Query(UUID gameId, int eraNumber, int roundNumber, UUID callerPlayerId) {}

    record Result(
            int eraNumber,
            int roundNumber,
            String status,
            int timerRemainingSeconds,
            int submittedCount,
            int totalPlayers,
            List<UUID> pendingPlayerIds) {}
}
