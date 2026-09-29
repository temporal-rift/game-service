package io.github.temporalrift.game.session.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.session.domain.port.out.PlayerReconnectSagaRepository;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaState;
import io.github.temporalrift.game.session.domain.saga.PlayerReconnectSagaStatus;

@PersistenceIntegrationTest
class PlayerReconnectSagaAdapterIT {

    @Autowired
    PlayerReconnectSagaRepository repository;

    @Autowired
    PlayerReconnectSagaStateJpaRepository jpaRepository;

    @Test
    void gameDueQuery_includesTheBoundaryAndExcludesFutureTerminalAndOtherGames() {
        var gameId = UUID.randomUUID();
        var decisionAt = Instant.parse("2026-01-01T00:00:30Z");
        var past = save(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt.minusMillis(1));
        var exact = save(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt);
        save(gameId, PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt.plusMillis(1));
        save(gameId, PlayerReconnectSagaStatus.RECONNECTED, decisionAt);
        save(gameId, PlayerReconnectSagaStatus.ABANDONED, decisionAt);
        save(UUID.randomUUID(), PlayerReconnectSagaStatus.GRACE_PERIOD, decisionAt);

        assertThat(repository.findGracePeriodsDueBy(gameId, decisionAt)).containsExactlyInAnyOrder(past, exact);
        assertThat(repository.compareAndSetStatus(
                        exact.sagaId(), PlayerReconnectSagaStatus.GRACE_PERIOD, PlayerReconnectSagaStatus.ABANDONED))
                .isTrue();
        assertThat(repository.compareAndSetStatus(
                        exact.sagaId(), PlayerReconnectSagaStatus.GRACE_PERIOD, PlayerReconnectSagaStatus.ABANDONED))
                .isFalse();
        assertThat(repository.findGracePeriodsDueBy(gameId, decisionAt)).containsExactly(past);
    }

    @Test
    void activeLookup_excludesCompletedHistoryFromPreviousDisconnects() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var deadline = Instant.parse("2026-01-01T00:00:30Z");
        repository.save(new PlayerReconnectSagaState(
                UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.RECONNECTED, deadline));
        repository.save(new PlayerReconnectSagaState(
                UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.COMPLETED, deadline));
        var active = repository.save(new PlayerReconnectSagaState(
                UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.GRACE_PERIOD, deadline.plusSeconds(30)));
        assertThat(repository.findActiveGracePeriod(gameId, playerId)).contains(active);
        repository.compareAndSetStatus(
                active.sagaId(), PlayerReconnectSagaStatus.GRACE_PERIOD, PlayerReconnectSagaStatus.RECONNECTED);
        assertThat(repository.findActiveGracePeriod(gameId, playerId)).isEmpty();
    }

    @Test
    void duplicateActivePeriod_isRejectedByTheDatabase() {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var deadline = Instant.parse("2026-01-01T00:00:30Z");
        repository.save(new PlayerReconnectSagaState(
                UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.GRACE_PERIOD, deadline));
        repository.save(new PlayerReconnectSagaState(
                UUID.randomUUID(), gameId, playerId, PlayerReconnectSagaStatus.GRACE_PERIOD, deadline.plusSeconds(1)));
        assertThatThrownBy(jpaRepository::flush).isInstanceOf(DataIntegrityViolationException.class);
    }

    private PlayerReconnectSagaState save(UUID gameId, PlayerReconnectSagaStatus status, Instant deadline) {
        return repository.save(
                new PlayerReconnectSagaState(UUID.randomUUID(), gameId, UUID.randomUUID(), status, deadline));
    }
}
