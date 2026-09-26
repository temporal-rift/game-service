package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.shared.domain.model.CardCategory;
import io.github.temporalrift.game.shared.domain.model.CardGrade;
import io.github.temporalrift.game.shared.domain.model.CardType;
import io.github.temporalrift.game.shared.domain.model.Faction;
import io.github.temporalrift.game.shared.domain.model.SpecialAction;

record StoredSubmittedAction(
        String type,
        UUID playerId,
        UUID cardInstanceId,
        String cardType,
        String cardGrade,
        String faction,
        String specialAction,
        UUID sourceEventId,
        UUID targetEventId,
        List<UUID> targetEventIds,
        UUID sourceOutcomeId,
        UUID targetOutcomeId,
        UUID targetPlayerId,
        List<UUID> targetPlayerIds,
        String disguiseCategory) {

    StoredSubmittedAction {
        targetEventIds = targetEventIds == null ? null : List.copyOf(targetEventIds);
        targetPlayerIds = targetPlayerIds == null ? null : List.copyOf(targetPlayerIds);
    }

    StoredSubmittedAction(
            String type,
            UUID playerId,
            UUID cardInstanceId,
            String cardType,
            String cardGrade,
            String faction,
            String specialAction,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {
        this(
                type,
                playerId,
                cardInstanceId,
                cardType,
                cardGrade,
                faction,
                specialAction,
                null,
                targetEventId,
                null,
                sourceOutcomeId,
                targetOutcomeId,
                targetPlayerId,
                null,
                null);
    }

    StoredSubmittedAction(
            String type,
            UUID playerId,
            UUID cardInstanceId,
            String cardType,
            String faction,
            String specialAction,
            UUID targetEventId,
            UUID sourceOutcomeId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {
        this(
                type,
                playerId,
                cardInstanceId,
                cardType,
                null,
                faction,
                specialAction,
                null,
                targetEventId,
                null,
                sourceOutcomeId,
                targetOutcomeId,
                targetPlayerId,
                null,
                null);
    }

    static StoredSubmittedAction fromDomain(SubmittedAction action) {
        return switch (action) {
            case SubmittedAction.CardAction(
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
                    CardCategory disguiseCategory) ->
                new StoredSubmittedAction(
                        "CARD",
                        playerId,
                        cardInstanceId,
                        cardType.name(),
                        grade.name(),
                        null,
                        null,
                        null,
                        targetEventId,
                        targetEventIds,
                        sourceOutcomeId,
                        targetOutcomeId,
                        targetPlayerId,
                        targetPlayerIds,
                        disguiseCategory == null ? null : disguiseCategory.name());
            case SubmittedAction.SpecialActionSubmission(
                    UUID playerId,
                    Faction faction,
                    SpecialAction specialAction,
                    UUID sourceEventId,
                    UUID sourceOutcomeId,
                    UUID targetEventId,
                    UUID targetOutcomeId,
                    UUID targetPlayerId) ->
                new StoredSubmittedAction(
                        "SPECIAL",
                        playerId,
                        null,
                        null,
                        null,
                        faction.name(),
                        specialAction.name(),
                        sourceEventId,
                        targetEventId,
                        null,
                        sourceOutcomeId,
                        targetOutcomeId,
                        targetPlayerId,
                        null,
                        null);
        };
    }

    SubmittedAction toDomain() {
        return switch (type) {
            case "CARD" -> {
                var resolvedCardType = CardType.valueOf(cardType);
                // Rows stored before the player-target list existed carry a scalar NULLIFY target.
                var resolvedPlayerIds = targetPlayerIds;
                if (resolvedPlayerIds == null && resolvedCardType == CardType.NULLIFY && targetPlayerId != null) {
                    resolvedPlayerIds = List.of(targetPlayerId);
                }
                // Rows stored before Decoy declared a disguise carry none; they keep Decoy's own category.
                var resolvedDisguise = disguiseCategory == null ? null : CardCategory.valueOf(disguiseCategory);
                if (resolvedDisguise == null && resolvedCardType == CardType.DECOY) {
                    resolvedDisguise = CardType.DECOY.getCategory();
                }
                yield new SubmittedAction.CardAction(
                        playerId,
                        cardInstanceId,
                        resolvedCardType,
                        cardGrade == null ? CardGrade.I : CardGrade.valueOf(cardGrade),
                        targetEventId,
                        targetEventIds,
                        sourceOutcomeId,
                        targetOutcomeId,
                        targetPlayerId,
                        resolvedPlayerIds,
                        resolvedDisguise);
            }
            case "SPECIAL" ->
                new SubmittedAction.SpecialActionSubmission(
                        playerId,
                        Faction.valueOf(faction),
                        SpecialAction.valueOf(specialAction),
                        sourceEventId,
                        sourceOutcomeId,
                        targetEventId,
                        targetOutcomeId,
                        targetPlayerId);
            default -> throw new IllegalStateException("Unknown submitted action type: " + type);
        };
    }
}
