package io.github.temporalrift.game.action.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.PersistenceIntegrationTest;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;

/**
 * Proves the participant-roster lock really serializes concurrent transactions against Postgres, which no mock can
 * show: a phase opening and a participant adoption for the same game and era must not both run unaware of the other.
 */
@PersistenceIntegrationTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ParadoxResolutionRosterLockIT {

    private static final int ERA = 2;

    @Autowired
    ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate newTransaction;

    @BeforeEach
    void setUp() {
        newTransaction = new TransactionTemplate(transactionManager);
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void rosterLock_isHeldUntilCommitForTheSameGameAndEraOnly() throws Exception {
        var gameId = UUID.randomUUID();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = CompletableFuture.runAsync(() -> newTransaction.executeWithoutResult(_ -> {
            paradoxResolutionPhaseRepository.lockParticipantRoster(gameId, ERA);
            locked.countDown();
            await(release);
        }));
        try {
            await(locked);

            assertThatThrownBy(() -> lockWithTimeout(gameId, ERA)).isInstanceOf(DataAccessException.class);
            assertThatCode(() -> lockWithTimeout(gameId, ERA + 1)).doesNotThrowAnyException();
            assertThatCode(() -> lockWithTimeout(UUID.randomUUID(), ERA)).doesNotThrowAnyException();
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }

        assertThatCode(() -> lockWithTimeout(gameId, ERA)).doesNotThrowAnyException();
    }

    /** Fails fast instead of waiting when another transaction holds the same roster lock. */
    private void lockWithTimeout(UUID gameId, int eraNumber) {
        newTransaction.executeWithoutResult(_ -> {
            jdbcTemplate.execute("SET LOCAL lock_timeout = '250ms'");
            paradoxResolutionPhaseRepository.lockParticipantRoster(gameId, eraNumber);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the roster-lock holder");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
