package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryPlayerScoreJpaRepository extends InMemoryJpaRepository<PlayerScoreJpaEntity, UUID>
        implements PlayerScoreJpaRepository {

    InMemoryPlayerScoreJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, PlayerScoreJpaEntity>of("player_score", PlayerScoreJpaEntity::getId)
                        .unique("game_player", score -> key(score.getGameId(), score.getPlayerId())));
    }

    @Override
    public List<PlayerScoreJpaEntity> findAllByGameId(UUID gameId) {
        return findMatching(score -> score.getGameId().equals(gameId));
    }

    @Override
    public Optional<PlayerScoreJpaEntity> findByGameIdAndPlayerId(UUID gameId, UUID playerId) {
        return findFirst(
                score -> score.getGameId().equals(gameId) && score.getPlayerId().equals(playerId));
    }

    @Override
    public List<PlayerScoreJpaEntity> findAllByGameIdWithLock(UUID gameId) {
        return findMatchingForUpdate(
                score -> score.getGameId().equals(gameId), Comparator.comparing(PlayerScoreJpaEntity::getPlayerId));
    }

    @Override
    public void upsert(UUID id, UUID gameId, UUID playerId, String faction, int totalScore) {
        lockForUpsert(key("player_score", gameId, playerId));
        var score = findByGameIdAndPlayerId(gameId, playerId).orElseGet(() -> {
            var created = new PlayerScoreJpaEntity();
            created.setId(id);
            created.setGameId(gameId);
            created.setPlayerId(playerId);
            return created;
        });
        score.setFaction(faction);
        score.setTotalScore(totalScore);
        save(score);
    }
}
