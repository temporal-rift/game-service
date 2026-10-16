package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemorySpecialActionEraUsageJpaRepository extends InMemoryJpaRepository<SpecialActionEraUsageJpaEntity, UUID>
        implements SpecialActionEraUsageJpaRepository {

    InMemorySpecialActionEraUsageJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, SpecialActionEraUsageJpaEntity>of(
                                "special_action_era_usage", SpecialActionEraUsageJpaEntity::getId)
                        .unique(
                                "game_era_player",
                                usage -> List.of(usage.getGameId(), usage.getEraNumber(), usage.getPlayerId())));
    }

    @Override
    public Optional<SpecialActionEraUsageJpaEntity> findByGameIdAndEraNumberAndPlayerId(
            UUID gameId, int eraNumber, UUID playerId) {
        return findFirst(usage -> usage.getGameId().equals(gameId)
                && usage.getEraNumber() == eraNumber
                && usage.getPlayerId().equals(playerId));
    }

    @Override
    public int countErasClaiming(UUID gameId, UUID playerId, String special) {
        return findMatching(usage -> usage.getGameId().equals(gameId)
                        && usage.getPlayerId().equals(playerId)
                        && usage.getClaimedSpecials().contains(special))
                .size();
    }
}
