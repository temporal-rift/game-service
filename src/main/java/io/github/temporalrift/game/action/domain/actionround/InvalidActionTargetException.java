package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.SpecialAction;

public class InvalidActionTargetException extends RuntimeException {

    public InvalidActionTargetException(String message) {
        super(message);
    }

    public static InvalidActionTargetException swingRequiresSourceOutcome() {
        return new InvalidActionTargetException("Swing action requires a sourceOutcomeId");
    }

    public static InvalidActionTargetException swingRequiresTargetOutcome() {
        return new InvalidActionTargetException("Swing action requires a targetOutcomeId");
    }

    public static InvalidActionTargetException swingRequiresDistinctOutcomes() {
        return new InvalidActionTargetException("Swing action requires distinct sourceOutcomeId and targetOutcomeId");
    }

    public static InvalidActionTargetException specialActionRequiresTarget(SpecialAction specialAction) {
        return new InvalidActionTargetException(specialAction + " requires a targetEventId and a targetOutcomeId");
    }

    public static InvalidActionTargetException specialActionRequiresTargetEvent(SpecialAction specialAction) {
        return new InvalidActionTargetException(specialAction + " requires a targetEventId");
    }

    public static InvalidActionTargetException specialActionRequiresTargetPlayer(SpecialAction specialAction) {
        return new InvalidActionTargetException(specialAction + " requires a targetPlayerId");
    }

    public static InvalidActionTargetException corruptCannotTargetSelf() {
        return new InvalidActionTargetException("Corrupt cannot target the submitting player");
    }
}
