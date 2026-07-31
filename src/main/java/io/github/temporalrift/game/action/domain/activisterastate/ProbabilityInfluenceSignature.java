package io.github.temporalrift.game.action.domain.activisterastate;

import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.shared.CardType;

/** Observable shape of a probability-influence card used by the Activist's Expose action. */
public record ProbabilityInfluenceSignature(
        CardType type, UUID targetEventId, UUID sourceOutcomeId, UUID targetOutcomeId) {

    public ProbabilityInfluenceSignature {
        if (!isSupported(type)) {
            throw new IllegalArgumentException("Expose supports only Push, Suppress, and Swing signatures");
        }
    }

    public static Optional<ProbabilityInfluenceSignature> from(SubmittedAction action) {
        if (action instanceof SubmittedAction.CardAction card && isSupported(card.cardType())) {
            return Optional.of(new ProbabilityInfluenceSignature(
                    card.cardType(), card.targetEventId(), card.sourceOutcomeId(), card.targetOutcomeId()));
        }
        return Optional.empty();
    }

    private static boolean isSupported(CardType cardType) {
        return cardType == CardType.PUSH || cardType == CardType.SUPPRESS || cardType == CardType.SWING;
    }
}
