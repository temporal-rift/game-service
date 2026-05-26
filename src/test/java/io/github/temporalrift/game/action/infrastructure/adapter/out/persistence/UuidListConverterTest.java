package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidListConverterTest {

    final UuidListConverter converter = new UuidListConverter();

    @Test
    @DisplayName("convertToDatabaseColumn returns null when attribute is null")
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    @DisplayName("convertToDatabaseColumn serializes UUID list to JSON")
    void convertToDatabaseColumn_list_serializesToJson() {
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        var json = converter.convertToDatabaseColumn(ids);

        assertThat(json).contains(ids.getFirst().toString(), ids.getLast().toString());
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list when db value is null")
    void convertToEntityAttribute_null_returnsEmptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    @DisplayName("convertToEntityAttribute deserializes UUID JSON")
    void convertToEntityAttribute_json_deserializes() {
        var id = UUID.randomUUID();

        var result = converter.convertToEntityAttribute("[\"" + id + "\"]");

        assertThat(result).containsExactly(id);
    }

    @Test
    @DisplayName("convertToEntityAttribute throws when JSON is malformed")
    void convertToEntityAttribute_malformedJson_throws() {
        assertThatIllegalStateException()
                .isThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                .withMessageContaining("deserialize");
    }
}
