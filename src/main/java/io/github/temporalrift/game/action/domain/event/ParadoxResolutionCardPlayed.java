package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

public record ParadoxResolutionCardPlayed(
        UUID gameId,
        int eraNumber,
        UUID playerId,
        UUID cardInstanceId,
        CardType cardType,
        CardGrade grade,
        UUID targetEventId,
        UUID targetOutcomeId)
        implements ActionEventPayload {

    public ParadoxResolutionCardPlayed(
            UUID gameId,
            int eraNumber,
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            UUID targetEventId,
            UUID targetOutcomeId) {
        this(gameId, eraNumber, playerId, cardInstanceId, cardType, CardGrade.I, targetEventId, targetOutcomeId);
    }
}
