package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextActionFactsReadyJpaRepository
        extends InMemoryJpaRepository<ScoringContextActionFactsReadyJpaEntity, GameEraKey>
        implements ScoringContextActionFactsReadyJpaRepository {

    private final Clock clock;

    InMemoryScoringContextActionFactsReadyJpaRepository(InMemoryDatabase database, Clock clock) {
        super(
                database,
                TableDefinition.of(
                        "scoring_context_action_facts_ready", ScoringContextActionFactsReadyJpaEntity::getId));
        this.clock = clock;
    }

    @Override
    public void insertIfAbsent(UUID gameId, int eraNumber) {
        insertRowIfAbsent(new ScoringContextActionFactsReadyJpaEntity(gameId, eraNumber, clock.instant()));
    }
}
