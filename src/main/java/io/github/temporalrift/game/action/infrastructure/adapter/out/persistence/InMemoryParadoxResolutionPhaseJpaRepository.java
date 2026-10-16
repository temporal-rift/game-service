package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryParadoxResolutionPhaseJpaRepository extends InMemoryJpaRepository<ParadoxResolutionPhaseJpaEntity, UUID>
        implements ParadoxResolutionPhaseJpaRepository {

    private final InMemoryDatabase database;

    InMemoryParadoxResolutionPhaseJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ParadoxResolutionPhaseJpaEntity>of(
                                "paradox_resolution_phase", ParadoxResolutionPhaseJpaEntity::getId)
                        .unique("game_era", phase -> List.of(phase.getGameId(), phase.getEraNumber())));
        this.database = database;
    }

    @Override
    public Optional<ParadoxResolutionPhaseJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findFirst(phase -> phase.getGameId().equals(gameId) && phase.getEraNumber() == eraNumber);
    }

    @Override
    public Optional<ParadoxResolutionPhaseJpaEntity> findByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber) {
        return findFirstForUpdate(phase -> phase.getGameId().equals(gameId) && phase.getEraNumber() == eraNumber);
    }

    @Override
    public int lockTransactionScoped(String lockKey) {
        database.lockUntilTransactionEnds(lockKey);
        return 1;
    }
}
