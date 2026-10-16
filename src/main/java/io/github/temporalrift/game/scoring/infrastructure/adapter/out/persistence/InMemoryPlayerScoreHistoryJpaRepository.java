package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryPlayerScoreHistoryJpaRepository extends InMemoryJpaRepository<PlayerScoreHistoryJpaEntity, UUID>
        implements PlayerScoreHistoryJpaRepository {

    InMemoryPlayerScoreHistoryJpaRepository(InMemoryDatabase database) {
        super(database, TableDefinition.of("player_score_history", PlayerScoreHistoryJpaEntity::getId));
    }

    @Override
    public long countByPlayerScoreId(UUID playerScoreId) {
        return findMatching(history -> history.getPlayerScoreId().equals(playerScoreId))
                .size();
    }

    @Override
    public List<PlayerScoreHistoryJpaEntity> findAllByGameId(UUID gameId) {
        return findMatching(history -> history.getGameId().equals(gameId));
    }

    @Override
    public List<PlayerScoreHistoryJpaEntity> findAllByGameIdOrderByEraNumberAsc(UUID gameId) {
        return findMatching(
                history -> history.getGameId().equals(gameId),
                Comparator.comparingInt(PlayerScoreHistoryJpaEntity::getEraNumber));
    }
}
