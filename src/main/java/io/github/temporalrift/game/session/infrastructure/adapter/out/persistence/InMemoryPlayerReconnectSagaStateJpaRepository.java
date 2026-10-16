package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;

import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryDatabase;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryJpaRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.InMemoryRepository;
import io.github.temporalrift.game.shared.infrastructure.adapter.out.memory.TableDefinition;

@InMemoryRepository
class InMemoryPlayerReconnectSagaStateJpaRepository
        extends InMemoryJpaRepository<PlayerReconnectSagaStateJpaEntity, UUID>
        implements PlayerReconnectSagaStateJpaRepository {

    private static final String GRACE_PERIOD = "GRACE_PERIOD";

    InMemoryPlayerReconnectSagaStateJpaRepository(InMemoryDatabase database) {
        super(
                database,
                TableDefinition.<UUID, PlayerReconnectSagaStateJpaEntity>of(
                                "player_reconnect_saga_state", PlayerReconnectSagaStateJpaEntity::getSagaId)
                        .unique(
                                "idx_reconnect_saga_active_game_player",
                                saga -> GRACE_PERIOD.equals(saga.getStatus())
                                        ? List.of(saga.getGameId(), saga.getPlayerId())
                                        : null));
    }

    @Override
    public Optional<PlayerReconnectSagaStateJpaEntity> findByGameIdAndPlayerIdAndStatus(
            UUID gameId, UUID playerId, String status) {
        return findFirst(saga -> saga.getGameId().equals(gameId)
                && saga.getPlayerId().equals(playerId)
                && saga.getStatus().equals(status));
    }

    @Override
    public List<PlayerReconnectSagaStateJpaEntity> findAllByStatusAndGraceExpiresAtLessThanEqualOrderByGraceExpiresAt(
            String status, Instant deadline, Limit limit) {
        var due = findMatching(
                saga -> saga.getStatus().equals(status)
                        && !saga.getGraceExpiresAt().isAfter(deadline),
                Comparator.comparing(PlayerReconnectSagaStateJpaEntity::getGraceExpiresAt));
        return limit.isLimited() ? due.stream().limit(limit.max()).toList() : due;
    }

    @Override
    public List<PlayerReconnectSagaStateJpaEntity> findAllByGameIdAndStatusAndGraceExpiresAtLessThanEqual(
            UUID gameId, String status, Instant decisionAt) {
        return findMatching(saga -> saga.getGameId().equals(gameId)
                && saga.getStatus().equals(status)
                && !saga.getGraceExpiresAt().isAfter(decisionAt));
    }

    @Override
    public int compareAndSetStatus(UUID sagaId, String expected, String next) {
        return findByIdForUpdate(sagaId)
                .filter(saga -> saga.getStatus().equals(expected))
                .map(saga -> {
                    saga.setStatus(next);
                    save(saga);
                    return 1;
                })
                .orElse(0);
    }
}
