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
class InMemoryScoringContextRevisionistActionJpaRepository
        extends InMemoryJpaRepository<ScoringContextRevisionistActionJpaEntity, UUID>
        implements ScoringContextRevisionistActionJpaRepository {

    InMemoryScoringContextRevisionistActionJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextRevisionistActionJpaEntity>of(
                                "scoring_context_revisionist_action", ScoringContextRevisionistActionJpaEntity::getId)
                        .unique(
                                "game_era_player_action_targets",
                                action -> key(
                                        action.getGameId(),
                                        action.getEraNumber(),
                                        action.getPlayerId(),
                                        action.getAction(),
                                        action.getTargetEventId(),
                                        action.getTargetOutcomeId())));
    }

    @Override
    public int insertIfAbsent(
            UUID id,
            UUID gameId,
            int eraNumber,
            UUID playerId,
            String action,
            UUID targetEventId,
            UUID targetOutcomeId) {
        var row = new ScoringContextRevisionistActionJpaEntity();
        row.setId(id);
        row.setGameId(gameId);
        row.setEraNumber(eraNumber);
        row.setPlayerId(playerId);
        row.setAction(action);
        row.setTargetEventId(targetEventId);
        row.setTargetOutcomeId(targetOutcomeId);
        return insertRowIfAbsent(row) ? 1 : 0;
    }

    @Override
    public List<ScoringContextRevisionistActionJpaEntity> findAllUnresolvedWithLock(UUID gameId, int eraNumber) {
        return findMatchingForUpdate(
                action -> action.getGameId().equals(gameId)
                        && action.getEraNumber() == eraNumber
                        && action.getResolved() == null,
                Comparator.comparing(action -> action.getId().toString()));
    }

    @Override
    public boolean existsByGameIdAndEraNumberAndResolvedIsNull(UUID gameId, int eraNumber) {
        return !findMatching(action -> action.getGameId().equals(gameId)
                        && action.getEraNumber() == eraNumber
                        && action.getResolved() == null)
                .isEmpty();
    }
}
