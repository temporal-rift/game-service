package io.github.temporalrift.game.action.domain.event;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;

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
        UUID targetPlayerId,
        List<UUID> targetPlayerIds,
        CardCategory disguiseCategory)
        implements ActionEventPayload {

    public CardPlayed {
        targetEventIds = targetEventIds == null ? null : List.copyOf(targetEventIds);
        targetPlayerIds = targetPlayerIds == null ? null : List.copyOf(targetPlayerIds);
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
                targetPlayerId,
                null,
                null);
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
                targetPlayerId,
                null,
                null);
    }
}
