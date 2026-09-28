package io.github.temporalrift.game.session.domain.lobby;

/** A player's connection to an in-progress game; {@link #ABANDONED} is final and forfeits every win condition. */
public enum ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    ABANDONED
}
