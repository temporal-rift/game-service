package io.github.temporalrift.game.action.domain.activisterastate;

import io.github.temporalrift.game.shared.SpecialAction;

/** The mutually exclusive modes for an Activist's declaration of record. */
public enum ActivistDeclarationMode {
    RALLY(SpecialAction.RALLY),
    MOMENTUM(SpecialAction.MOMENTUM);

    private final SpecialAction specialAction;

    ActivistDeclarationMode(SpecialAction specialAction) {
        this.specialAction = specialAction;
    }

    public SpecialAction toSpecialAction() {
        return specialAction;
    }
}
