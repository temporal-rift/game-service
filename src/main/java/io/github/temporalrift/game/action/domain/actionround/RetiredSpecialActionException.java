package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

/**
 * Raised when a player submits a special that is no longer offered ({@code UNRAVEL}, unreachable with at
 * most one Weaver per game). Distinct from {@link InvalidSpecialActionException} so a client can tell "this
 * special is retired" apart from "this special isn't yours".
 */
public final class RetiredSpecialActionException extends RuntimeException {

    public RetiredSpecialActionException(SpecialAction specialAction) {
        super(specialAction + " is retired and is no longer offered to any faction");
    }
}
