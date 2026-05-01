package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

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
        // when
        var result = converter.convertToDatabaseColumn(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("convertToDatabaseColumn serializes UUID list to JSON string")
    void convertToDatabaseColumn_list_returnsJson() {
        // given
        var id = UUID.randomUUID();
        var ids = List.of(id);

        // when
        var result = converter.convertToDatabaseColumn(ids);

        // then
        assertThat(result).contains(id.toString());
    }

    @Test
    @DisplayName("convertToEntityAttribute returns empty list when dbData is null")
    void convertToEntityAttribute_null_returnsEmptyList() {
        // when
        var result = converter.convertToEntityAttribute(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("convertToEntityAttribute deserializes JSON to UUID list")
    void convertToEntityAttribute_json_returnsList() {
        // given
        var id = UUID.randomUUID();
        var json = String.format("[\"%s\"]", id);

        // when
        var result = converter.convertToEntityAttribute(json);

        // then
        assertThat(result).containsExactly(id);
    }

    @Test
    @DisplayName("convertToEntityAttribute throws IllegalStateException for malformed JSON")
    void convertToEntityAttribute_malformedJson_throwsIllegalStateException() {
        // when / then
        assertThatIllegalStateException()
                .isThrownBy(() -> converter.convertToEntityAttribute("not-json"))
                .withMessageContaining("deserialize");
    }

    @Test
    @DisplayName("round-trip serialization preserves all UUIDs")
    void roundTrip_preservesAllUuids() {
        // given
        var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        // when
        var json = converter.convertToDatabaseColumn(ids);
        var restored = converter.convertToEntityAttribute(json);

        // then
        assertThat(restored).isEqualTo(ids);
    }
}
