package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;

@Converter
class OutcomeDefinitionListConverter
        implements AttributeConverter<List<FutureEventDefinitionPort.OutcomeDefinition>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<FutureEventDefinitionPort.OutcomeDefinition> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute.stream()
                    .map(outcome -> new OutcomeRow(outcome.outcomeId(), outcome.initialProbability()))
                    .toList());
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize future event outcomes", e);
        }
    }

    @Override
    public List<FutureEventDefinitionPort.OutcomeDefinition> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<OutcomeRow>>() {}).stream()
                    .map(row ->
                            new FutureEventDefinitionPort.OutcomeDefinition(row.outcomeId(), row.initialProbability()))
                    .toList();
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize future event outcomes", e);
        }
    }

    private record OutcomeRow(UUID outcomeId, int initialProbability) {}
}
