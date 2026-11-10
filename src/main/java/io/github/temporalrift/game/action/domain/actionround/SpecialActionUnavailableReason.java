package io.github.temporalrift.game.action.domain.actionround;

/** Why a faction special cannot be submitted in a round regardless of its target. */
public enum SpecialActionUnavailableReason {
    JAMMED,
    DECLARATION_WINDOW_ONLY,
    NOT_ELIGIBLE_THIS_ERA,
    NOT_ELIGIBLE_THIS_ROUND,
    GAME_BUDGET_EXHAUSTED,
    ERA_BUDGET_EXHAUSTED
}
