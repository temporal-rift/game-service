package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Converter
class UuidListConverter implements AttributeConverter<List<UUID>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<UUID> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize UUID list", e);
        }
    }

    @Override
    public List<UUID> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize UUID list", e);
        }
    }
}
