package io.github.temporalrift.game.action.domain.actionround;

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
}
