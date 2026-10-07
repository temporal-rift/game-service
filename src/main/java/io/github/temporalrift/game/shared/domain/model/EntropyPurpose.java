package io.github.temporalrift.game.shared.domain.model;

/** Semantic purpose of a gameplay-affecting random choice; each purpose draws from its own stream. */
public enum EntropyPurpose {
    FACTION_ASSIGNMENT,
    EVENT_DECK_SHUFFLE,
    CARD_DEAL,
    HAND_TIMEOUT_SELECTION,
    INTERCEPT_REVEAL,
    INTERCEPT_DECOY
}
