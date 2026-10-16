package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.stereotype.Component;

import io.github.temporalrift.game.simulation.domain.port.out.OutboxProbe;

@Component
@Profile("in-memory")
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class InMemoryOutboxProbe implements OutboxProbe {

    private final EventPublicationRepository publications;

    InMemoryOutboxProbe(EventPublicationRepository publications) {
        this.publications = publications;
    }

    @Override
    public int pendingPublications() {
        return publications.findIncompletePublications().size();
    }
}
