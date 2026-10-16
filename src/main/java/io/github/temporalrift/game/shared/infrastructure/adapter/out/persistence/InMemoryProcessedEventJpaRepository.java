package io.github.temporalrift.game.shared.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryProcessedEventJpaRepository
        extends InMemoryJpaRepository<ProcessedEventJpaEntity, ProcessedEventJpaEntity.ProcessedEventKey>
        implements ProcessedEventJpaRepository {

    private final Clock clock;

    InMemoryProcessedEventJpaRepository(InMemoryDatabase database, Clock clock) {
        super(database, TableDefinition.of("processed_events", ProcessedEventJpaEntity::getId));
        this.clock = clock;
    }

    @Override
    public int insertIfAbsent(UUID eventId, String consumer) {
        return insertRowIfAbsent(new ProcessedEventJpaEntity(eventId, consumer, clock.instant())) ? 1 : 0;
    }
}
