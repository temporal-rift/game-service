package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;
import io.github.temporalrift.game.session.domain.port.out.SessionGameRulesPort;

@Component
class FutureEventCatalogAdapter implements FutureEventCatalogPort {

    private final List<FutureEventDefinition> events;
    private final Map<UUID, FutureEventDefinition> eventsById;

    FutureEventCatalogAdapter(FutureEventCatalogProperties properties, SessionGameRulesPort rules) {
        events = properties.events();
        if (events.isEmpty()) {
            throw new IllegalStateException("Future event catalog must not be empty");
        }
        eventsById = events.stream()
                .collect(Collectors.toMap(FutureEventDefinition::eventId, Function.identity(), (first, second) -> {
                    throw new IllegalStateException("Future event catalog repeats event ID " + first.eventId());
                }));
        var bounds = rules.probabilityBounds();
        events.forEach(event -> event.requireStartWithin(bounds));
    }

    @Override
    public List<UUID> allEventIds() {
        return events.stream().map(FutureEventDefinition::eventId).toList();
    }

    @Override
    public List<FutureEventDefinition> findByEventIds(List<UUID> eventIds) {
        return eventIds.stream()
                .map(id -> {
                    var event = eventsById.get(id);
                    if (event == null) {
                        throw new IllegalStateException("Event ID " + id + " not found in catalog");
                    }
                    return event;
                })
                .toList();
    }
}
