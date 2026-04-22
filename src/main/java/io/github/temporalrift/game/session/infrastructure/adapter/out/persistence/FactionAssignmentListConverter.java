package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.session.domain.saga.FactionAssignment;

@Converter
class FactionAssignmentListConverter implements AttributeConverter<List<FactionAssignment>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<FactionAssignment> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize faction assignments", e);
        }
    }

    @Override
    public List<FactionAssignment> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<FactionAssignment>>() {});
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize faction assignments", e);
        }
    }
}
