package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.shared.CardGrade;
import io.github.temporalrift.game.shared.CardType;
import io.github.temporalrift.game.shared.Faction;
import io.github.temporalrift.game.shared.SpecialAction;

record StoredSubmittedAction(
        String type,
        UUID playerId,
        UUID cardInstanceId,
        String cardType,
        String cardGrade,
        String faction,
        String specialAction,
        UUID targetEventId,
        List<UUID> targetEventIds,
        UUID sourceOutcomeId,
        UUID targetOutcomeId,
        UUID targetPlayerId) {

    StoredSubmittedAction {
        targetEventIds = targetEventIds == null ? null : List.copyOf(targetEventIds);
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
                targetEventId,
                null,
                sourceOutcomeId,
                targetOutcomeId,
                targetPlayerId);
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
                targetEventId,
                null,
                sourceOutcomeId,
                targetOutcomeId,
                targetPlayerId);
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
                    UUID targetPlayerId) ->
                new StoredSubmittedAction(
                        "CARD",
                        playerId,
                        cardInstanceId,
                        cardType.name(),
                        grade.name(),
                        null,
                        null,
                        targetEventId,
                        targetEventIds,
                        sourceOutcomeId,
                        targetOutcomeId,
                        targetPlayerId);
            case SubmittedAction.SpecialActionSubmission(
                    UUID playerId,
                    Faction faction,
                    SpecialAction specialAction,
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
                        targetEventId,
                        null,
                        null,
                        targetOutcomeId,
                        targetPlayerId);
        };
    }

    SubmittedAction toDomain() {
        return switch (type) {
            case "CARD" ->
                new SubmittedAction.CardAction(
                        playerId,
                        cardInstanceId,
                        CardType.valueOf(cardType),
                        cardGrade == null ? CardGrade.I : CardGrade.valueOf(cardGrade),
                        targetEventId,
                        targetEventIds,
                        sourceOutcomeId,
                        targetOutcomeId,
                        targetPlayerId);
            case "SPECIAL" ->
                new SubmittedAction.SpecialActionSubmission(
                        playerId,
                        Faction.valueOf(faction),
                        SpecialAction.valueOf(specialAction),
                        targetEventId,
                        targetOutcomeId,
                        targetPlayerId);
            default -> throw new IllegalStateException("Unknown submitted action type: " + type);
        };
    }
}
