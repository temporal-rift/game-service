package io.github.temporalrift.game.session.application.saga;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface PlayerReconnectSaga {

    Optional<StartResult> start(UUID gameId, UUID playerId);

    void handleReconnect(UUID gameId, UUID playerId);

    record StartResult(UUID sagaId, Instant graceExpiresAt) {}
}
