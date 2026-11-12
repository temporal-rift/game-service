package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * The target-independent era and round rules of the faction specials. Submission validation and the
 * per-round availability report both read these predicates, so what a client is told can never drift from
 * what {@code submitAction} accepts.
 */
public final class SpecialActionRoundRules {

    /** Expose is only playable in this action round of an era. */
    public static final int EXPOSE_ROUND = 2;

    /** Obscure covers the following round of its era, which this round does not have. */
    public static final int OBSCURE_INELIGIBLE_ROUND = 3;

    private SpecialActionRoundRules() {}

    /** Rally and Momentum are declared in the declaration window and are never submitted in an action round. */
    public static boolean declarationWindowOnly(SpecialAction specialAction) {
        return specialAction == SpecialAction.RALLY || specialAction == SpecialAction.MOMENTUM;
    }

    /** Cascade only erases again when its event carries into a next era, which the final era never has. */
    public static boolean ineligibleThisEra(SpecialAction specialAction, int eraNumber, int maxEras) {
        return specialAction == SpecialAction.CASCADE && eraNumber >= maxEras;
    }

    public static boolean obscureInFinalRound(SpecialAction specialAction, int roundNumber) {
        return specialAction == SpecialAction.OBSCURE && roundNumber == OBSCURE_INELIGIBLE_ROUND;
    }

    public static boolean exposeOutsideItsRound(SpecialAction specialAction, int roundNumber) {
        return specialAction == SpecialAction.EXPOSE && roundNumber != EXPOSE_ROUND;
    }

    public static boolean annihilateBeforeEarliestRound(
            SpecialAction specialAction, int roundNumber, int annihilateEarliestRound) {
        return specialAction == SpecialAction.ANNIHILATE && roundNumber < annihilateEarliestRound;
    }

    public static boolean rewriteAfterLatestRound(
            SpecialAction specialAction, int roundNumber, int rewriteLatestRound) {
        return specialAction == SpecialAction.REWRITE && roundNumber > rewriteLatestRound;
    }

    /** Whether the special's round rules (Obscure, Expose, Annihilate, Rewrite) make it unplayable in this round. */
    public static boolean ineligibleThisRound(
            SpecialAction specialAction, int roundNumber, SpecialActionRoundWindows windows) {
        return obscureInFinalRound(specialAction, roundNumber)
                || exposeOutsideItsRound(specialAction, roundNumber)
                || annihilateBeforeEarliestRound(specialAction, roundNumber, windows.annihilateEarliestRound())
                || rewriteAfterLatestRound(specialAction, roundNumber, windows.rewriteLatestRound());
    }
}
