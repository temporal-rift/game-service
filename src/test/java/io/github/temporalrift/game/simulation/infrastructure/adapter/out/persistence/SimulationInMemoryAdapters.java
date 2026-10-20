package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The in-memory simulation adapters are package-private, so the in-memory test configuration reaches them here. */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    InMemoryClockOperationRepository.class,
    InMemoryEntropyDecisionRepository.class,
    InMemoryExecutionRepository.class,
    InMemoryGameplayDiscard.class,
    InMemoryOutboxProbe.class
})
public class SimulationInMemoryAdapters {}
