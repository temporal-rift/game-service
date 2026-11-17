package io.github.temporalrift.game.scoring.infrastructure.adapter.out.persistence;

import static io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition.key;

import java.util.List;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryScoringContextStabilizeJpaRepository extends InMemoryJpaRepository<ScoringContextStabilizeJpaEntity, UUID>
        implements ScoringContextStabilizeJpaRepository {

    InMemoryScoringContextStabilizeJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextStabilizeJpaEntity>of(
                                "scoring_context_stabilize", ScoringContextStabilizeJpaEntity::getId)
                        .unique(
                                "game_era_player_target",
                                stabilize -> key(
                                        stabilize.getGameId(),
                                        stabilize.getEraNumber(),
                                        stabilize.getPlayerId(),
                                        stabilize.getTargetEventId())));
    }

    @Override
    public List<ScoringContextStabilizeJpaEntity> findAllByGameIdAndEraNumber(UUID gameId, int eraNumber) {
        return findMatching(stabilize -> stabilize.getGameId().equals(gameId) && stabilize.getEraNumber() == eraNumber);
    }

    @Override
    public void insertIfAbsent(UUID id, UUID gameId, int eraNumber, UUID playerId, UUID targetEventId) {
        var stabilize = new ScoringContextStabilizeJpaEntity();
        stabilize.setId(id);
        stabilize.setGameId(gameId);
        stabilize.setEraNumber(eraNumber);
        stabilize.setPlayerId(playerId);
        stabilize.setTargetEventId(targetEventId);
        insertRowIfAbsent(stabilize);
    }
}
