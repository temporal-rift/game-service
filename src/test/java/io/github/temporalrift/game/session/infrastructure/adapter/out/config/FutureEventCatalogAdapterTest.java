package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition.OutcomeDefinition;
import io.github.temporalrift.game.session.domain.futureevent.ProbabilityBounds;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;

class FutureEventCatalogAdapterTest {

    private static final ProbabilityBounds BOUNDS = new ProbabilityBounds(0, 90);

    static FutureEventDefinition event(int first, int second, int third) {
        return new FutureEventDefinition(
                UUID.randomUUID(),
                "Event Title",
                List.of(
                        new OutcomeDefinition(UUID.randomUUID(), "Outcome A", first),
                        new OutcomeDefinition(UUID.randomUUID(), "Outcome B", second),
                        new OutcomeDefinition(UUID.randomUUID(), "Outcome C", third)));
    }

    static List<FutureEventDefinition> catalogOf(int size) {
        return IntStream.range(0, size).mapToObj(i -> event(33, 33, 34)).toList();
    }

    static FutureEventCatalogAdapter adapter(List<FutureEventDefinition> events, ProbabilityBounds bounds) {
        var rules = mock(SessionGameRulesPort.class);
        when(rules.probabilityBounds()).thenReturn(bounds);
        return new FutureEventCatalogAdapter(new FutureEventCatalogProperties(events), rules);
    }

    @Test
    @DisplayName("allEventIds returns all IDs from the catalog")
    void allEventIds_returnsAllIds() {
        // given
        var events = catalogOf(30);
        var adapter = adapter(events, BOUNDS);

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
        var adapter = adapter(events, BOUNDS);
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
        var adapter = adapter(catalogOf(30), BOUNDS);
        var unknownId = UUID.randomUUID();

        var ids = List.of(unknownId);

        // when / then
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> adapter.findByEventIds(ids))
                .withMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("an empty catalog is rejected")
    void emptyCatalog_isRejected() {
        List<FutureEventDefinition> empty = List.of();

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> adapter(empty, BOUNDS));
    }

    @Test
    @DisplayName("a catalog repeating an event ID is rejected")
    void duplicateEventId_isRejected() {
        var event = event(50, 30, 20);
        var events = List.of(event, event);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> adapter(events, BOUNDS))
                .withMessageContaining(event.eventId().toString());
    }

    @Test
    @DisplayName("a card printing a weight above the configured ceiling is rejected at construction")
    void cardAboveCeiling_isRejected() {
        var invalid = event(92, 5, 3);
        var events = List.of(event(50, 30, 20), invalid);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter(events, BOUNDS))
                .withMessageContaining(invalid.eventId().toString());
    }

    @Test
    @DisplayName("a card printing a zero weight is rejected at construction")
    void cardWithZeroWeight_isRejected() {
        var events = List.of(event(50, 50, 0));

        assertThatIllegalArgumentException().isThrownBy(() -> adapter(events, BOUNDS));
    }

    @Test
    @DisplayName("the shipped catalog passes validation against the shipped bounds and is not uniform")
    void shippedCatalog_isValidAndDistinct() throws IOException {
        var catalog = bind("future-events.yml", "game.catalog", FutureEventCatalogProperties.class);
        var probability =
                bind("application-test.yml", "game.rules.probability", SessionRulesProperties.Probability.class);

        var adapter = adapter(catalog.events(), new ProbabilityBounds(probability.floor(), probability.ceiling()));

        assertThat(adapter.allEventIds()).hasSize(30);
        var distributions = catalog.events().stream()
                .map(event -> event.outcomes().stream()
                        .map(OutcomeDefinition::probability)
                        .toList())
                .collect(Collectors.toSet());
        assertThat(distributions).hasSizeGreaterThan(1);
    }

    private static <T> T bind(String resource, String prefix, Class<T> type) throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind(prefix, type)
                .orElseThrow(IllegalStateException::new);
    }
}
