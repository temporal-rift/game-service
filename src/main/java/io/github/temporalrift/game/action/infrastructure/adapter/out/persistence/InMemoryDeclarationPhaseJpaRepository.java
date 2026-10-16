package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.action.domain.declarationphase.DeclarationPhaseStatus;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryDeclarationPhaseJpaRepository extends InMemoryJpaRepository<DeclarationPhaseJpaEntity, UUID>
        implements DeclarationPhaseJpaRepository {

    private static final String OPEN = DeclarationPhaseStatus.OPEN.name();

    InMemoryDeclarationPhaseJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, DeclarationPhaseJpaEntity>of(
                                "declaration_phase", DeclarationPhaseJpaEntity::getId)
                        .unique("game_era", phase -> List.of(phase.getGameId(), phase.getEraNumber())));
    }

    @Override
    public int insertIfAbsent(UUID id, UUID gameId, int eraNumber, String status, Instant expiresAt) {
        var phase = new DeclarationPhaseJpaEntity();
        phase.setId(id);
        phase.setGameId(gameId);
        phase.setEraNumber(eraNumber);
        phase.setStatus(status);
        phase.setExpiresAt(expiresAt);
        return insertRowIfAbsent(phase) ? 1 : 0;
    }

    @Override
    public Optional<DeclarationPhaseJpaEntity> findByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findFirst(phase -> phase.getGameId().equals(gameId) && phase.getEraNumber() == eraNumber);
    }

    @Override
    public Optional<DeclarationPhaseJpaEntity> findByGameIdAndEraNumberWithLock(UUID gameId, int eraNumber) {
        return findFirstForUpdate(phase -> phase.getGameId().equals(gameId) && phase.getEraNumber() == eraNumber);
    }

    @Override
    public Optional<DeclarationPhaseJpaEntity> findByIdWithLock(UUID id) {
        return findByIdForUpdate(id);
    }

    @Override
    public List<UUID> findOpenDueIds(Instant now) {
        return findMatching(phase ->
                        OPEN.equals(phase.getStatus()) && !phase.getExpiresAt().isAfter(now))
                .stream()
                .map(DeclarationPhaseJpaEntity::getId)
                .toList();
    }
}
