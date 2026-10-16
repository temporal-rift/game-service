package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextChainFactJpaRepository extends InMemoryJpaRepository<ScoringContextChainFactJpaEntity, UUID>
        implements ScoringContextChainFactJpaRepository {

    InMemoryScoringContextChainFactJpaRepository(InMemoryDatabase database) {
        super(database, TableDefinition.of("scoring_context_chain_fact", ScoringContextChainFactJpaEntity::getId));
    }

    @Override
    public List<ScoringContextChainFactJpaEntity> findAllByGameIdAndConsumedFalseWithLock(UUID gameId) {
        return findMatchingForUpdate(
                fact -> fact.getGameId().equals(gameId) && !fact.isConsumed(),
                Comparator.comparing(fact -> fact.getId().toString()));
    }
}
