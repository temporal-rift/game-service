package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryTable;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;
import io.github.temporalrift.game.simulation.domain.execution.Execution;
import io.github.temporalrift.game.simulation.domain.port.out.ExecutionRepository;

@Repository
@Profile("in-memory")
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class InMemoryExecutionRepository implements ExecutionRepository {

    private static final int SINGLETON = 1;
    private static final TableDefinition<Integer, Execution> EXECUTION =
            TableDefinition.of("simulation_execution", execution -> SINGLETON);

    private final InMemoryTable<Integer, Execution> store;

    InMemoryExecutionRepository(InMemoryDatabase database) {
        this.store = database.table(EXECUTION);
    }

    @Override
    public Optional<Execution> find() {
        return store.find(SINGLETON);
    }

    @Override
    public Optional<Execution> findWithLock() {
        return store.findForUpdate(SINGLETON);
    }

    @Override
    public boolean insertIfAbsent(Execution execution) {
        return store.insertIfAbsent(truncated(execution));
    }

    @Override
    public void update(Execution execution) {
        store.findForUpdate(SINGLETON)
                .ifPresent(stored -> store.save(new Execution(
                        stored.context(),
                        execution.revision(),
                        execution.logicalTime().truncatedTo(ChronoUnit.MICROS))));
    }

    private static Execution truncated(Execution execution) {
        return new Execution(
                execution.context(),
                execution.revision(),
                execution.logicalTime().truncatedTo(ChronoUnit.MICROS));
    }
}
