package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;
import io.github.temporalrift.game.session.domain.port.out.FutureEventCatalogPort;

@ConfigurationProperties("game.catalog")
public record FutureEventCatalogAdapter(List<FutureEventDefinition> events) implements FutureEventCatalogPort {

    public FutureEventCatalogAdapter {
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            throw new IllegalStateException("Future event catalog must not be empty");
        }
        events = List.copyOf(events);
    }

    @Override
    public List<UUID> allEventIds() {
        return events.stream().map(FutureEventDefinition::eventId).toList();
    }

    @Override
    public List<FutureEventDefinition> findByEventIds(List<UUID> eventIds) {
        Map<UUID, FutureEventDefinition> eventMap =
                events.stream().collect(Collectors.toMap(FutureEventDefinition::eventId, e -> e));
        return eventIds.stream()
                .map(id -> {
                    var event = eventMap.get(id);
                    if (event == null) {
                        throw new IllegalStateException("Event ID " + id + " not found in catalog");
                    }
                    return event;
                })
                .toList();
    }
}
