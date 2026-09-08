package io.github.temporalrift.game.action.domain.event;

import java.util.List;
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
        List<UUID> targetEventIds,
        UUID sourceOutcomeId,
        UUID targetOutcomeId,
        UUID targetPlayerId)
        implements ActionEventPayload {

    public CardPlayed {
        targetEventIds = targetEventIds == null ? null : List.copyOf(targetEventIds);
    }

    public CardPlayed(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            CardGrade grade,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {
        this(
                gameId,
                eraNumber,
                roundNumber,
                playerId,
                cardInstanceId,
                cardType,
                grade,
                targetEventId,
                null,
                sourceOutcomeId,
                targetOutcomeId,
                targetPlayerId);
    }

    public CardPlayed(
            UUID gameId,
            int eraNumber,
            int roundNumber,
            UUID playerId,
            UUID cardInstanceId,
            CardType cardType,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {
        this(
                gameId,
                eraNumber,
                roundNumber,
                playerId,
                cardInstanceId,
                cardType,
                CardGrade.I,
                targetEventId,
                null,
                sourceOutcomeId,
                targetOutcomeId,
                targetPlayerId);
    }
}
