package io.github.temporalrift.game.session.infrastructure.adapter.out.config;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.temporalrift.game.session.domain.futureevent.FutureEventDefinition;

@ConfigurationProperties("game.catalog")
public record FutureEventCatalogProperties(List<FutureEventDefinition> events) {

    public FutureEventCatalogProperties {
        Objects.requireNonNull(events, "events must not be null");
        events = List.copyOf(events);
    }
}
