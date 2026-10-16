package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.modulith.events.core.EventSerializer;

/** Keeps the Spring Modulith event publication registry in the in-memory store. */
@Configuration(proxyBeanMethods = false)
@Profile("in-memory")
class InMemoryEventPublicationConfiguration {

    @Bean
    EventPublicationRepository eventPublicationRepository(InMemoryDatabase database, EventSerializer serializer) {
        return new InMemoryEventPublicationRepository(database, serializer);
    }
}
