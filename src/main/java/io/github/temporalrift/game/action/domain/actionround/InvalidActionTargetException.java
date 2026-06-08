package io.github.temporalrift.game.action.domain.actionround;

import java.util.UUID;

import io.github.temporalrift.events.shared.CardType;

public class InvalidActionTargetException extends RuntimeException {

    public InvalidActionTargetException(CardType cardType, UUID sourceOutcomeId, UUID targetOutcomeId) {
        super(message(cardType, sourceOutcomeId, targetOutcomeId));
    }

    private static String message(CardType cardType, UUID sourceOutcomeId, UUID targetOutcomeId) {
        if (cardType == CardType.SWING) {
            if (sourceOutcomeId == null) {
                return "Swing action requires a sourceOutcomeId";
            }
            if (targetOutcomeId == null) {
                return "Swing action requires a targetOutcomeId";
            }
            if (sourceOutcomeId.equals(targetOutcomeId)) {
                return "Swing action requires distinct sourceOutcomeId and targetOutcomeId";
            }
        }
        return "Invalid action target";
    }
}
