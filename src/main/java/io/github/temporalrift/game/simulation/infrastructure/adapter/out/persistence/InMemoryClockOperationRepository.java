package io.github.temporalrift.game.simulation.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryTable;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;
import io.github.temporalrift.game.simulation.domain.execution.ClockOperation;
import io.github.temporalrift.game.simulation.domain.port.out.ClockOperationRepository;

@Repository
@Profile("in-memory")
@ConditionalOnProperty(name = "game.simulation.enabled", havingValue = "true")
class InMemoryClockOperationRepository implements ClockOperationRepository {

    private static final TableDefinition<UUID, ClockOperation> OPERATIONS = TableDefinition.of(
            "simulation_clock_operation", operation -> operation.request().operationId());

    private final InMemoryTable<UUID, ClockOperation> store;

    InMemoryClockOperationRepository(InMemoryDatabase database) {
        this.store = database.table(OPERATIONS);
    }

    @Override
    public Optional<ClockOperation> find(UUID operationId) {
        return store.find(operationId);
    }

    @Override
    public void save(ClockOperation operation) {
        if (!store.insertIfAbsent(operation)) {
            throw new DuplicateKeyException(
                    "Clock operation " + operation.request().operationId() + " is already recorded");
        }
    }

    @Override
    public void clear() {
        store.deleteAll(operation -> true);
    }
}
