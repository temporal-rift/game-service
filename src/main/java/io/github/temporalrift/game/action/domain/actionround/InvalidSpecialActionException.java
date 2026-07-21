package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

public class InvalidSpecialActionException extends RuntimeException {

    public InvalidSpecialActionException(Faction faction, SpecialAction specialAction) {
        super("Faction " + faction + " does not own special action " + specialAction);
    }
}
