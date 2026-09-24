package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.domain.model.SpecialAction;

public class SpecialActionNotEligibleForRoundException extends RuntimeException {

    public SpecialActionNotEligibleForRoundException(SpecialAction specialAction, int eraNumber, int roundNumber) {
        super(specialAction + " cannot be played in era " + eraNumber + " round " + roundNumber);
    }
}
