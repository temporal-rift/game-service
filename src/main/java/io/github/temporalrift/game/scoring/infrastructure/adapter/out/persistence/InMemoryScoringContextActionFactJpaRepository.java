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
class InMemoryScoringContextActionFactJpaRepository
        extends InMemoryJpaRepository<ScoringContextActionFactJpaEntity, UUID>
        implements ScoringContextActionFactJpaRepository {

    InMemoryScoringContextActionFactJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ScoringContextActionFactJpaEntity>of(
                                "scoring_context_action_fact", ScoringContextActionFactJpaEntity::getId)
                        .unique(
                                "game_era_player_reason",
                                fact -> key(
                                        fact.getGameId(), fact.getEraNumber(), fact.getPlayerId(), fact.getReason())));
    }

    @Override
    public int insertIfAbsent(ActionFactRow row) {
        var fact = new ScoringContextActionFactJpaEntity();
        fact.setId(row.id());
        fact.setGameId(row.gameId());
        fact.setEraNumber(row.eraNumber());
        fact.setPlayerId(row.playerId());
        fact.setFaction(row.faction());
        fact.setReason(row.reason());
        fact.setConsumed(false);
        fact.setTargetEventId(row.targetEventId());
        fact.setTargetOutcomeId(row.targetOutcomeId());
        return insertRowIfAbsent(fact) ? 1 : 0;
    }

    @Override
    public List<ScoringContextActionFactJpaEntity> findAllUnconsumedWithLock(UUID gameId, int eraNumber) {
        return findMatchingForUpdate(
                fact -> fact.getGameId().equals(gameId) && fact.getEraNumber() == eraNumber && !fact.isConsumed(),
                Comparator.comparing(fact -> fact.getId().toString()));
    }
}
