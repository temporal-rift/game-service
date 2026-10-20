package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** The SQL-backed simulation adapters are package-private, so the persistence test configuration reaches them here. */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    ClockOperationRepositoryAdapter.class,
    EntropyDecisionRepositoryAdapter.class,
    ExecutionRepositoryAdapter.class,
    GameplayDiscardAdapter.class,
    OutboxProbeAdapter.class
})
public class SimulationJdbcAdapters {}
