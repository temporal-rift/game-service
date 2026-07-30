package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.SpecialAction;

/** Raised when a declaration-only Activist special is submitted as a normal round action. */
public final class DeclarationSpecialActionRequiredException extends RuntimeException {

    public DeclarationSpecialActionRequiredException(SpecialAction specialAction) {
        super(specialAction + " must be recorded during the Activist declaration window");
    }
}
