package io.github.temporalrift.game.session.application.saga;

import java.util.UUID;

interface PlayerReconnectSaga {

    void start(UUID gameId, UUID playerId);

    void handleReconnect(UUID gameId, UUID playerId);

    void handleTimerExpiry(UUID sagaId);
}
