package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryActivistEraStateJpaRepository extends InMemoryJpaRepository<ActivistEraStateJpaEntity, UUID>
        implements ActivistEraStateJpaRepository {

    InMemoryActivistEraStateJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ActivistEraStateJpaEntity>of(
                                "activist_era_state", ActivistEraStateJpaEntity::getId)
                        .unique(
                                "game_era_activist",
                                state ->
                                        List.of(state.getGameId(), state.getEraNumber(), state.getActivistPlayerId())));
    }

    @Override
    public Optional<ActivistEraStateJpaEntity> findByGameIdAndEraNumberAndActivistPlayerId(
            UUID gameId, int eraNumber, UUID activistPlayerId) {
        return findFirst(state -> state.getGameId().equals(gameId)
                && state.getEraNumber() == eraNumber
                && state.getActivistPlayerId().equals(activistPlayerId));
    }

    @Override
    public List<ActivistEraStateJpaEntity> findAllByGameIdAndEraNumberAndDeclarationModeIsNotNull(
            UUID gameId, int eraNumber) {
        return findMatching(state -> state.getGameId().equals(gameId)
                && state.getEraNumber() == eraNumber
                && state.getDeclarationMode() != null);
    }

    @Override
    public List<ActivistEraStateJpaEntity> findAllByGameIdAndEraNumberAndExposedPlayerIdIsNotNull(
            UUID gameId, int eraNumber) {
        return findMatching(state -> state.getGameId().equals(gameId)
                && state.getEraNumber() == eraNumber
                && state.getExposedPlayerId() != null);
    }
}
