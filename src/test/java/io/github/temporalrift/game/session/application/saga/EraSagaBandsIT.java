package io.github.temporalrift.game.session.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.GameServiceIntegrationTest;
import io.github.temporalrift.game.session.domain.port.out.EraSagaRepository;
import io.github.temporalrift.game.session.domain.saga.EraSagaState;
import io.github.temporalrift.game.session.domain.saga.EraSagaStatus;
import io.github.temporalrift.game.shared.domain.event.ActionRoundClosed;
import io.github.temporalrift.game.shared.domain.event.StartActionRoundRequested;

@GameServiceIntegrationTest
class EraSagaBandsIT {

    @Autowired
    EraSagaRepository repository;

    @Autowired
    EraSagaAdvancer advancer;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEventMulticaster multicaster;

    @Test
    void earlyReadiness_survivesReloadAndOpensRoundThreeAfterClosure() {
        var gameId = createSaga();
        advancer.handleBandsPublished(gameId, 2);

        var reloaded = repository.findByGameId(gameId).orElseThrow();
        assertThat(reloaded.bandsPublished()).isTrue();
        assertThat(reloaded.status()).isEqualTo(EraSagaStatus.WAITING_ROUND_2);
        assertThat(roundCount(gameId)).isZero();
        assertThat(timerCount(gameId)).isZero();

        advancer.handleRoundClosed(gameId, closure(gameId));
        awaitRound(gameId);
    }

    @Test
    void lateReadiness_keepsRoundAndTimerClosedUntilPublished() {
        var gameId = createSaga();
        advancer.handleRoundClosed(gameId, closure(gameId));

        assertThat(repository.findByGameId(gameId).orElseThrow().status()).isEqualTo(EraSagaStatus.WAITING_BANDS);
        assertThat(roundCount(gameId)).isZero();
        assertThat(timerCount(gameId)).isZero();

        advancer.handleBandsPublished(gameId, 1);
        assertThat(roundCount(gameId)).isZero();
        advancer.handleBandsPublished(gameId, 2);
        awaitRound(gameId);
    }

    @Test
    void concurrentAndRepeatedFacts_requestRoundThreeExactlyOnce() throws Exception {
        var gameId = createSaga();
        var requests = new AtomicInteger();
        ApplicationListener<PayloadApplicationEvent<?>> observer = event -> {
            if (event.getPayload() instanceof StartActionRoundRequested request
                    && request.gameId().equals(gameId)) {
                requests.incrementAndGet();
            }
        };
        multicaster.addApplicationListener(observer);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var closed = executor.submit(() -> advancer.handleRoundClosed(gameId, closure(gameId)));
            var bands = executor.submit(() -> advancer.handleBandsPublished(gameId, 2));
            closed.get(10, TimeUnit.SECONDS);
            bands.get(10, TimeUnit.SECONDS);
            advancer.handleRoundClosed(gameId, closure(gameId));
            advancer.handleBandsPublished(gameId, 2);

            assertThat(requests).hasValue(1);
            awaitRound(gameId);
        } finally {
            multicaster.removeApplicationListener(observer);
        }
    }

    private UUID createSaga() {
        var gameId = UUID.randomUUID();
        transactions.executeWithoutResult(_ -> repository.save(
                new EraSagaState(gameId, 2, EraSagaStatus.WAITING_ROUND_2, List.of(UUID.randomUUID()))));
        return gameId;
    }

    private ActionRoundClosed closure(UUID gameId) {
        return new ActionRoundClosed(gameId, 2, 2, "ALL_SUBMITTED", 1);
    }

    private void awaitRound(UUID gameId) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(roundCount(gameId)).isEqualTo(1);
            assertThat(timerCount(gameId)).isEqualTo(1);
        });
    }

    private int roundCount(UUID gameId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM action_round WHERE game_id = ? AND era_number = 2 AND round_number = 3",
                Integer.class,
                gameId);
    }

    private int timerCount(UUID gameId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM action_round_saga_state "
                        + "WHERE game_id = ? AND era_number = 2 AND round_number = 3",
                Integer.class,
                gameId);
    }
}
