package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

/** Private INTERCEPT result revealing sampled hand cards to the intercepting viewer only. */
public record HandCardIntercepted(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID playerId,
        UUID targetPlayerId,
        List<RevealedCard> revealedCards)
        implements ActionEventPayload {

    public HandCardIntercepted {
        revealedCards = List.copyOf(revealedCards);
    }

    public record RevealedCard(UUID cardInstanceId, CardType cardType, CardGrade grade) {}
}
