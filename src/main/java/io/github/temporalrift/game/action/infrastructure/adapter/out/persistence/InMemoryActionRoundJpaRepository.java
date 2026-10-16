package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryActionRoundJpaRepository extends InMemoryJpaRepository<ActionRoundJpaEntity, UUID>
        implements ActionRoundJpaRepository {

    InMemoryActionRoundJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ActionRoundJpaEntity>of("action_round", ActionRoundJpaEntity::getId)
                        .unique(
                                "game_era_round",
                                round -> List.of(round.getGameId(), round.getEraNumber(), round.getRoundNumber())));
    }

    @Override
    public Optional<ActionRoundJpaEntity> findByGameIdAndEraNumberAndRoundNumber(
            UUID gameId, int eraNumber, int roundNumber) {
        return findFirst(round -> matches(round, gameId, eraNumber, roundNumber));
    }

    @Override
    public Optional<ActionRoundJpaEntity> findByGameIdAndEraNumberAndRoundNumberWithLock(
            UUID gameId, int eraNumber, int roundNumber) {
        return findFirstForUpdate(round -> matches(round, gameId, eraNumber, roundNumber));
    }

    private static boolean matches(ActionRoundJpaEntity round, UUID gameId, int eraNumber, int roundNumber) {
        return round.getGameId().equals(gameId)
                && round.getEraNumber() == eraNumber
                && round.getRoundNumber() == roundNumber;
    }
}
