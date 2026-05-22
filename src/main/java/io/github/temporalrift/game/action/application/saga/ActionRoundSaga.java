package io.github.temporalrift.game.action.application.saga;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ActionRoundSaga {

    void start(UUID gameId, int eraNumber, int roundNumber, List<UUID> playerIds);

    void handlePlayerSubmitted(UUID gameId, int eraNumber, int roundNumber, UUID playerId);

    void handleTimerExpiry(UUID sagaId);

    void rescheduleTimer(UUID sagaId, Instant timerExpiresAt);
}
