package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.events.shared.Faction;
import io.github.temporalrift.game.session.domain.saga.FactionAssignment;

class FactionAssignmentListConverterTest {

    final FactionAssignmentListConverter converter = new FactionAssignmentListConverter();

    @Test
    @DisplayName("convertToDatabaseColumn returns null when attribute is null")
    void convertToDatabaseColumn_null_returnsNull() {
        // when
        var result = converter.convertToDatabaseColumn(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("convertToDatabaseColumn serializes list to JSON string")
    void convertToDatabaseColumn_list_returnsJson() {
        // given
        var playerId = UUID.randomUUID();
        var assignments = List.of(new FactionAssignment(playerId, Faction.ERASERS));

        // when
        var result = converter.convertToDatabaseColumn(assignments);

        // then
        assertThat(result).contains(playerId.toString()).contains("ERASERS");
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
    @DisplayName("convertToEntityAttribute deserializes JSON to list")
    void convertToEntityAttribute_json_returnsList() {
        // given
        var playerId = UUID.randomUUID();
        var json = String.format("[{\"playerId\":\"%s\",\"faction\":\"PROPHETS\"}]", playerId);

        // when
        var result = converter.convertToEntityAttribute(json);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().playerId()).isEqualTo(playerId);
        assertThat(result.getFirst().faction()).isEqualTo(Faction.PROPHETS);
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
    @DisplayName("round-trip serialization preserves all assignments")
    void roundTrip_preservesAllAssignments() {
        // given
        var assignments = List.of(
                new FactionAssignment(UUID.randomUUID(), Faction.WEAVERS),
                new FactionAssignment(UUID.randomUUID(), Faction.ACTIVISTS));

        // when
        var json = converter.convertToDatabaseColumn(assignments);
        var restored = converter.convertToEntityAttribute(json);

        // then
        assertThat(restored).isEqualTo(assignments);
    }
}
