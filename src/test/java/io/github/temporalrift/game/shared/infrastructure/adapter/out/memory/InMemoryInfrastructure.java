package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The in-memory infrastructure configuration is package-private, so tests reach it through here. */
@TestConfiguration(proxyBeanMethods = false)
@Import({InMemoryInfrastructureConfiguration.class, InMemoryEventPublicationConfiguration.class})
public class InMemoryInfrastructure {}
