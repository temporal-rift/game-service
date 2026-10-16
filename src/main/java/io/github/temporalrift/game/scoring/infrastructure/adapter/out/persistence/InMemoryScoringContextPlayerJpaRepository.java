package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextPlayerJpaRepository extends InMemoryJpaRepository<ScoringContextPlayerJpaEntity, UUID>
        implements ScoringContextPlayerJpaRepository {

    InMemoryScoringContextPlayerJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextPlayerJpaEntity>of(
                                "scoring_context_player", ScoringContextPlayerJpaEntity::getId)
                        .unique("game_player", player -> key(player.getGameId(), player.getPlayerId())));
    }

    @Override
    public List<ScoringContextPlayerJpaEntity> findAllByGameId(UUID gameId) {
        return findMatching(player -> player.getGameId().equals(gameId));
    }

    @Override
    public Optional<ScoringContextPlayerJpaEntity> findByGameIdAndPlayerIdWithLock(UUID gameId, UUID playerId) {
        return findFirstForUpdate(player ->
                player.getGameId().equals(gameId) && player.getPlayerId().equals(playerId));
    }

    @Override
    public void upsert(UUID id, UUID gameId, UUID playerId, String faction) {
        lockForUpsert(key("scoring_context_player", gameId, playerId));
        var player = findFirst(row ->
                        row.getGameId().equals(gameId) && row.getPlayerId().equals(playerId))
                .orElseGet(() -> {
                    var created = new ScoringContextPlayerJpaEntity();
                    created.setId(id);
                    created.setGameId(gameId);
                    created.setPlayerId(playerId);
                    return created;
                });
        player.setFaction(faction);
        save(player);
    }
}
