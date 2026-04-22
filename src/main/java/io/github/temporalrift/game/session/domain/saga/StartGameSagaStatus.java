package io.github.temporalrift.game.session.domain.saga;

public enum StartGameSagaStatus {
    RUNNING,
    COMPLETED,
    COMPENSATING,
    FAILED,
    CANCELLED
}
