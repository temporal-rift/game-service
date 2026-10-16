package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryForesightRevealJpaRepository extends InMemoryJpaRepository<ForesightRevealJpaEntity, UUID>
        implements ForesightRevealJpaRepository {

    InMemoryForesightRevealJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, ForesightRevealJpaEntity>of(
                                "session_foresight_reveal", ForesightRevealJpaEntity::id)
                        .unique(
                                "game_era_player",
                                reveal -> List.of(reveal.gameId(), reveal.eraNumber(), reveal.playerId())));
    }

    @Override
    public boolean existsByGameIdAndEraNumberAndPlayerId(UUID gameId, int eraNumber, UUID playerId) {
        return findByGameIdAndEraNumberAndPlayerId(gameId, eraNumber, playerId).isPresent();
    }

    @Override
    public Optional<ForesightRevealJpaEntity> findByGameIdAndEraNumberAndPlayerId(
            UUID gameId, int eraNumber, UUID playerId) {
        return findFirst(reveal -> reveal.gameId().equals(gameId)
                && reveal.eraNumber() == eraNumber
                && reveal.playerId().equals(playerId));
    }
}
