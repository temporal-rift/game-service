package io.github.temporalrift.game.session.domain.saga;

public enum PlayerReconnectSagaStatus {
    GRACE_PERIOD,
    RECONNECTED,
    ABANDONED,
    COMPLETED
}
