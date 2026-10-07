package io.github.temporalrift.game.shared.domain.model;

/** Kind of gameplay-significant identity whose value takes part in rule ordering or player-visible references. */
public enum IdentityKind {
    LOBBY,
    GAME,
    CARD_INSTANCE,
    DECOY_CARD_INSTANCE,
    OFFERED_STABILIZE_CARD_INSTANCE,
    OFFERED_DETONATE_CARD_INSTANCE,
    DRAWN_EVENT,
    DRAWN_OUTCOME
}
