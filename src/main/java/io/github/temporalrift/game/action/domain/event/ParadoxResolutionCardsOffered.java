package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

/**
 * Private fact giving one participant, its sole viewer, their complete eligible resolution set for a
 * paradox-resolution phase: the eligible hand cards and the dealt Stabilize + Detonate offer. The list may be empty.
 */
public record ParadoxResolutionCardsOffered(UUID gameId, int eraNumber, UUID playerId, List<EligibleCard> cards)
        implements ActionEventPayload {

    public ParadoxResolutionCardsOffered {
        cards = List.copyOf(cards);
    }

    public record EligibleCard(UUID cardInstanceId, CardType cardType, CardGrade grade) {}
}
