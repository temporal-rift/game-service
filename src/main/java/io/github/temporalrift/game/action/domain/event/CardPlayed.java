package io.github.temporalrift.game.action.domain.event;

import java.util.UUID;

import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;

public record CardPlayed(
        UUID gameId,
        int eraNumber,
        int roundNumber,
        UUID playerId,
        UUID cardInstanceId,
        CardType cardType,
        CardGrade grade,
        UUID targetEventId,
        UUID sourceOutcomeId,
        UUID targetOutcomeId)
        implements ActionEventPayload {

    public CardPlayed(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId) {
        this(
                gameId,
                eraNumber,
                roundNumber,
                playerId,
                cardInstanceId,
                cardType,
                CardGrade.I,
                targetEventId,
                sourceOutcomeId,
                targetOutcomeId);
    }
}
