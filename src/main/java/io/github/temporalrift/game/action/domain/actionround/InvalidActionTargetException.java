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

    public static InvalidActionTargetException specialActionCannotTargetEvent(SpecialAction specialAction) {
        return new InvalidActionTargetException(specialAction + " cannot target an event");
    }

    public static InvalidActionTargetException cardRequiresTargetPlayer(CardType cardType) {
        return new InvalidActionTargetException(cardType + " requires a targetPlayerId");
    }

    public static InvalidActionTargetException cardCannotTargetEvent(CardType cardType) {
        return new InvalidActionTargetException(cardType + " cannot target an event");
    }

    public static InvalidActionTargetException cardRequiresTargetEvent(CardType cardType) {
        return new InvalidActionTargetException(cardType + " requires a targetEventId");
    }

    public static InvalidActionTargetException cardCannotTargetPlayer(CardType cardType) {
        return new InvalidActionTargetException(cardType + " cannot target a player");
    }

    public static InvalidActionTargetException cardCannotTargetSelf(CardType cardType) {
        return new InvalidActionTargetException(cardType + " cannot target the submitting player");
    }
}
