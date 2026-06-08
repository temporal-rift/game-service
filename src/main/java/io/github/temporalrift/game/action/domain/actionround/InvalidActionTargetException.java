package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

import io.github.temporalrift.events.shared.CardType;

public class InvalidActionTargetException extends RuntimeException {

    public InvalidActionTargetException(CardType cardType, UUID sourceOutcomeId, UUID targetOutcomeId) {
        super(message(cardType, sourceOutcomeId, targetOutcomeId));
    }

    private static String message(CardType cardType, UUID sourceOutcomeId, UUID targetOutcomeId) {
        if (cardType == CardType.SWING && sourceOutcomeId == null) {
            return "Swing action requires a sourceOutcomeId";
        }
        if (cardType == CardType.SWING && targetOutcomeId == null) {
            return "Swing action requires a targetOutcomeId";
        }
        if (cardType == CardType.SWING && targetOutcomeId != null && targetOutcomeId.equals(sourceOutcomeId)) {
            return "Swing action requires distinct sourceOutcomeId and targetOutcomeId";
        }
        return "Invalid action target";
    }
}
