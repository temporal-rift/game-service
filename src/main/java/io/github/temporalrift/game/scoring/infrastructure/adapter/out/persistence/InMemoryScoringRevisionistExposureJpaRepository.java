package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringRevisionistExposureJpaRepository
        extends InMemoryJpaRepository<ScoringRevisionistExposureJpaEntity, UUID>
        implements ScoringRevisionistExposureJpaRepository {

    InMemoryScoringRevisionistExposureJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringRevisionistExposureJpaEntity>of(
                                "scoring_revisionist_exposure", ScoringRevisionistExposureJpaEntity::getId)
                        .unique("game_player", exposure -> key(exposure.getGameId(), exposure.getPlayerId())));
    }

    @Override
    public List<UUID> findPlayerIdsByGameId(UUID gameId) {
        return findMatching(exposure -> exposure.getGameId().equals(gameId)).stream()
                .map(ScoringRevisionistExposureJpaEntity::getPlayerId)
                .toList();
    }

    @Override
    public int insertIfAbsent(UUID id, UUID gameId, UUID playerId) {
        var exposure = new ScoringRevisionistExposureJpaEntity();
        exposure.setId(id);
        exposure.setGameId(gameId);
        exposure.setPlayerId(playerId);
        return insertRowIfAbsent(exposure) ? 1 : 0;
    }
}
