package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextFulfillmentDeclarationJpaRepository
        extends InMemoryJpaRepository<ScoringContextFulfillmentDeclarationJpaEntity, UUID>
        implements ScoringContextFulfillmentDeclarationJpaRepository {

    InMemoryScoringContextFulfillmentDeclarationJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextFulfillmentDeclarationJpaEntity>of(
                                "scoring_context_fulfillment_declaration",
                                ScoringContextFulfillmentDeclarationJpaEntity::getId)
                        .unique(
                                "game_era_player_target",
                                declaration -> key(
                                        declaration.getGameId(),
                                        declaration.getEraNumber(),
                                        declaration.getPlayerId(),
                                        declaration.getTargetEventId())));
    }

    @Override
    public List<ScoringContextFulfillmentDeclarationJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findMatching(
                declaration -> declaration.getGameId().equals(gameId) && declaration.getEraNumber() == eraNumber);
    }

    @Override
    public void insertIfAbsent(UUID id, UUID gameId, int eraNumber, UUID playerId, UUID targetEventId) {
        var declaration = new ScoringContextFulfillmentDeclarationJpaEntity();
        declaration.setId(id);
        declaration.setGameId(gameId);
        declaration.setEraNumber(eraNumber);
        declaration.setPlayerId(playerId);
        declaration.setTargetEventId(targetEventId);
        insertRowIfAbsent(declaration);
    }
}
