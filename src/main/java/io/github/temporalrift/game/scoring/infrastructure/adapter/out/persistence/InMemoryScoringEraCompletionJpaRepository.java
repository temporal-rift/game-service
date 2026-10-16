package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.time.Clock;
import java.util.Comparator;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringEraCompletionJpaRepository extends InMemoryJpaRepository<ScoringEraCompletionJpaEntity, GameEraKey>
        implements ScoringEraCompletionJpaRepository {

    private final Clock clock;

    InMemoryScoringEraCompletionJpaRepository(InMemoryDatabase database, Clock clock) {
        super(database, TableDefinition.of("scoring_era_completion", ScoringEraCompletionJpaEntity::getId));
        this.clock = clock;
    }

    @Override
    public int insertIfAbsent(UUID gameId, int eraNumber) {
        return insertRowIfAbsent(new ScoringEraCompletionJpaEntity(gameId, eraNumber, clock.instant())) ? 1 : 0;
    }

    @Override
    public Integer findMaxEraNumberByGameId(UUID gameId) {
        return findMatching(completion -> completion.getId().getGameId().equals(gameId)).stream()
                .map(completion -> completion.getId().getEraNumber())
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
