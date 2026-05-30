package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.events.shared.CardType;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;

@Converter
class PlayerHandConverter implements AttributeConverter<List<PlayerState.CardInstance>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<PlayerState.CardInstance> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute.stream()
                    .map(card -> new CardInstanceRow(
                            card.cardInstanceId(), card.cardType().name()))
                    .toList());
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize player hand", e);
        }
    }

    @Override
    public List<PlayerState.CardInstance> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<CardInstanceRow>>() {}).stream()
                    .map(row -> new PlayerState.CardInstance(row.cardInstanceId(), CardType.valueOf(row.cardType())))
                    .toList();
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize player hand", e);
        }
    }

    private record CardInstanceRow(UUID cardInstanceId, String cardType) {}
}
