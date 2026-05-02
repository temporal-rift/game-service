package io.github.temporalrift.game.session.domain.saga;

import java.time.Instant;
import java.util.UUID;

public record PlayerReconnectSagaState(
        UUID sagaId, UUID gameId, UUID playerId, PlayerReconnectSagaStatus status, Instant graceExpiresAt) {

    public PlayerReconnectSagaState withStatus(PlayerReconnectSagaStatus newStatus) {
        return new PlayerReconnectSagaState(sagaId, gameId, playerId, newStatus, graceExpiresAt);
    }
}
