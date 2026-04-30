package io.github.temporalrift.game.session.domain.saga;

public enum EraSagaStatus {
    RUNNING,
    WAITING_ROUND_1,
    WAITING_ROUND_2,
    WAITING_ROUND_3,
    WAITING_SCORES,
    COMPLETED,
    FAILED
}
