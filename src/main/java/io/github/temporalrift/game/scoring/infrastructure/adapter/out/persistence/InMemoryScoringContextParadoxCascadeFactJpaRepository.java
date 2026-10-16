package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextParadoxCascadeFactJpaRepository
        extends InMemoryJpaRepository<ScoringContextParadoxCascadeFactJpaEntity, UUID>
        implements ScoringContextParadoxCascadeFactJpaRepository {

    InMemoryScoringContextParadoxCascadeFactJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.of(
                        "scoring_context_paradox_cascade_fact", ScoringContextParadoxCascadeFactJpaEntity::getId));
    }

    @Override
    public List<ScoringContextParadoxCascadeFactJpaEntity> findAllByGameIdAndConsumedFalseWithLock(UUID gameId) {
        return findMatchingForUpdate(
                fact -> fact.getGameId().equals(gameId) && !fact.isConsumed(),
                Comparator.comparing(fact -> fact.getId().toString()));
    }

    @Override
    public List<UUID> findDistinctConsumedAffectedEventIds(UUID gameId) {
        return findMatching(fact -> fact.getGameId().equals(gameId) && fact.isConsumed()).stream()
                .map(ScoringContextParadoxCascadeFactJpaEntity::getAffectedEventId)
                .distinct()
                .toList();
    }
}
