package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

public class SpecialActionNotEligibleForEraException extends RuntimeException {

    public SpecialActionNotEligibleForEraException(SpecialAction specialAction, int eraNumber) {
        super(specialAction + " cannot be played in era " + eraNumber);
    }
}
