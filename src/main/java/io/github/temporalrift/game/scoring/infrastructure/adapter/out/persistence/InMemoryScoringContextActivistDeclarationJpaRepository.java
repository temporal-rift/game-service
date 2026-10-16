package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextActivistDeclarationJpaRepository
        extends InMemoryJpaRepository<ScoringContextActivistDeclarationJpaEntity, UUID>
        implements ScoringContextActivistDeclarationJpaRepository {

    InMemoryScoringContextActivistDeclarationJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextActivistDeclarationJpaEntity>of(
                                "scoring_context_activist_declaration",
                                ScoringContextActivistDeclarationJpaEntity::getId)
                        .unique(
                                "game_era_player",
                                declaration -> key(
                                        declaration.getGameId(),
                                        declaration.getEraNumber(),
                                        declaration.getPlayerId())));
    }

    @Override
    public int insertIfAbsent(
            UUID id, UUID gameId, int eraNumber, UUID playerId, String mode, UUID targetEventId, UUID targetOutcomeId) {
        var declaration = new ScoringContextActivistDeclarationJpaEntity();
        declaration.setId(id);
        declaration.setGameId(gameId);
        declaration.setEraNumber(eraNumber);
        declaration.setPlayerId(playerId);
        declaration.setMode(mode);
        declaration.setTargetEventId(targetEventId);
        declaration.setTargetOutcomeId(targetOutcomeId);
        return insertRowIfAbsent(declaration) ? 1 : 0;
    }

    @Override
    public List<ScoringContextActivistDeclarationJpaEntity> findAllUnresolvedWithLock(UUID gameId, int eraNumber) {
        return findMatchingForUpdate(
                declaration -> declaration.getGameId().equals(gameId)
                        && declaration.getEraNumber() == eraNumber
                        && declaration.getResolutionSucceeded() == null,
                Comparator.comparing(declaration -> declaration.getId().toString()));
    }

    @Override
    public boolean existsByGameIdAndEraNumberAndResolutionSucceededIsNull(UUID gameId, int eraNumber) {
        return !findMatching(declaration -> declaration.getGameId().equals(gameId)
                        && declaration.getEraNumber() == eraNumber
                        && declaration.getResolutionSucceeded() == null)
                .isEmpty();
    }
}
