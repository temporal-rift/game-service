package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.events.shared.SpecialAction;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;

@Converter
class SubmittedActionListConverter implements AttributeConverter<List<SubmittedAction>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SubmittedAction> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(
                    attribute.stream().map(SubmittedActionRow::fromDomain).toList());
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize submitted actions", e);
        }
    }

    @Override
    public List<SubmittedAction> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return List.of();
        }
        try {
            var rows = MAPPER.readValue(dbData, new TypeReference<List<SubmittedActionRow>>() {});
            return rows.stream().map(SubmittedActionRow::toDomain).toList();
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize submitted actions", e);
        }
    }

    private record SubmittedActionRow(
            String type,
            UUID playerId,
            UUID cardInstanceId,
            String cardType,
            String faction,
            String specialAction,
            UUID targetEventId,
            UUID targetOutcomeId,
            UUID targetPlayerId) {

        static SubmittedActionRow fromDomain(SubmittedAction action) {
            return switch (action) {
                case SubmittedAction.CardAction(
                        UUID playerId,
                        UUID cardInstanceId,
                        CardType cardType,
                        UUID targetEventId,
                        UUID targetOutcomeId) ->
                    new SubmittedActionRow(
                            "CARD",
                            playerId,
                            cardInstanceId,
                            cardType.name(),
                            null,
                            null,
                            targetEventId,
                            targetOutcomeId,
                            null);
                case SubmittedAction.SpecialActionSubmission(
                        UUID playerId,
                        Faction faction,
                        SpecialAction specialAction,
                        UUID targetEventId,
                        UUID targetOutcomeId,
                        UUID targetPlayerId) ->
                    new SubmittedActionRow(
                            "SPECIAL",
                            playerId,
                            null,
                            null,
                            faction.name(),
                            specialAction.name(),
                            targetEventId,
                            targetOutcomeId,
                            targetPlayerId);
            };
        }

        SubmittedAction toDomain() {
            return switch (type) {
                case "CARD" ->
                    new SubmittedAction.CardAction(
                            playerId, cardInstanceId, CardType.valueOf(cardType), targetEventId, targetOutcomeId);
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
}
