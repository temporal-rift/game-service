package io.github.temporalrift.game.shared;

/** Identifies whether an event is new to the era or retained from the preceding resolution barrier. */
public enum CarryOverState {
    FRESH,
    CASCADED,
    STALLED
}
