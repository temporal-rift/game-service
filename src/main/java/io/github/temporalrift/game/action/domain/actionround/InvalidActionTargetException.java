package io.github.temporalrift.game.action.domain.actionround;

import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.SpecialAction;

public class InvalidActionTargetException extends RuntimeException {

    public InvalidActionTargetException(String message) {
        super(message);
    }

    public static InvalidActionTargetException requiresSourceOutcome(CardType cardType) {
        return new InvalidActionTargetException(cardType + " requires a sourceOutcomeId");
    }

    public static InvalidActionTargetException requiresTargetOutcome(CardType cardType) {
        return new InvalidActionTargetException(cardType + " requires a targetOutcomeId");
    }

    public static InvalidActionTargetException requiresDistinctOutcomes(CardType cardType) {
        return new InvalidActionTargetException(cardType + " requires distinct sourceOutcomeId and targetOutcomeId");
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
