package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition.OutcomeDefinition;

class FutureEventCatalogAdapterTest {

    static FutureEventDefinition event() {
        return new FutureEventDefinition(
                UUID.randomUUID(),
                "Event Title",
                List.of(
                        new OutcomeDefinition(UUID.randomUUID(), "Outcome A", 33),
                        new OutcomeDefinition(UUID.randomUUID(), "Outcome B", 33),
                        new OutcomeDefinition(UUID.randomUUID(), "Outcome C", 34)));
    }

    static List<FutureEventDefinition> catalogOf(int size) {
        return IntStream.range(0, size).mapToObj(i -> event()).toList();
    }

    @Test
    @DisplayName("allEventIds returns all IDs from the catalog")
    void allEventIds_returnsAllIds() {
        // given
        var events = catalogOf(30);
        var adapter = new FutureEventCatalogAdapter(events);

        // when
        var ids = adapter.allEventIds();

        // then
        assertThat(ids)
                .hasSize(30)
                .containsExactlyElementsOf(
                        events.stream().map(FutureEventDefinition::eventId).toList());
    }

    @Test
    @DisplayName("findByEventIds returns definitions in the same order as the input list")
    void findByEventIds_returnsDefinitionsInInputOrder() {
        // given
        var events = catalogOf(30);
        var adapter = new FutureEventCatalogAdapter(events);
        var ids = List.of(
                events.get(2).eventId(), events.get(0).eventId(), events.get(15).eventId());

        // when
        var result = adapter.findByEventIds(ids);

        // then
        assertThat(result).containsExactly(events.get(2), events.get(0), events.get(15));
    }

    @Test
    @DisplayName("findByEventIds throws IllegalStateException for an ID not in the catalog")
    void findByEventIds_missingId_throwsIllegalStateException() {
        // given
        var adapter = new FutureEventCatalogAdapter(catalogOf(30));
        var unknownId = UUID.randomUUID();

        // when / then
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> adapter.findByEventIds(List.of(unknownId)))
                .withMessageContaining(unknownId.toString());
    }
}
