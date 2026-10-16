package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.time.Instant;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringGameVisibilityJpaRepository extends InMemoryJpaRepository<ScoringGameVisibilityJpaEntity, UUID>
        implements ScoringGameVisibilityJpaRepository {

    InMemoryScoringGameVisibilityJpaRepository(InMemoryDatabase database) {
        super(database, TableDefinition.of("scoring_game_visibility", ScoringGameVisibilityJpaEntity::getGameId));
    }

    @Override
    public void upsertRevealed(UUID gameId, Instant updatedAt) {
        lockForUpsert(key("scoring_game_visibility", gameId));
        var visibility = findById(gameId).orElseGet(() -> {
            var created = new ScoringGameVisibilityJpaEntity();
            created.setGameId(gameId);
            return created;
        });
        visibility.setFactionsRevealed(true);
        visibility.setUpdatedAt(updatedAt);
        save(visibility);
    }
}
