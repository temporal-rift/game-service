package io.github.temporalrift.game.action.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.temporalrift.game.TestcontainersConfiguration;
import io.github.temporalrift.game.action.application.port.in.PlayCardUseCase;
import io.github.temporalrift.game.action.application.port.in.PlayParadoxResolutionCardUseCase;
import io.github.temporalrift.game.action.domain.actionround.ActionRound;
import io.github.temporalrift.game.action.domain.actionround.ActionRoundConfig;
import io.github.temporalrift.game.action.domain.actionround.SubmittedAction;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.DuplicateParadoxResolutionSubmissionException;
import io.github.temporalrift.game.action.domain.paradoxresolutionphase.ParadoxResolutionPhase;
import io.github.temporalrift.game.action.domain.playerstate.PlayerState;
import io.github.temporalrift.game.action.domain.port.out.ActionRoundRepository;
import io.github.temporalrift.game.action.domain.port.out.FutureEventDefinitionPort;
import io.github.temporalrift.game.action.domain.port.out.ParadoxResolutionPhaseRepository;
import io.github.temporalrift.game.action.domain.port.out.PlayerStateRepository;
import io.github.temporalrift.game.shared.CardType;

/**
 * Concurrency coverage for the simultaneous-submission path. Simultaneous submission is the core game
 * mechanic, so the submit path must not lose updates when two players submit into the same round at once.
 *
 * <p>These tests exercise the real {@link PlayCardUseCase} handler and repositories against a
 * Testcontainers Postgres — the pessimistic row lock only exists at the database level, so it cannot be
 * verified with mocks.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlayCardConcurrencyIT {

    private static final int ERA = 1;
    private static final int ROUND = 1;
    private static final int TIMER_SECONDS = 45;

    @Autowired
    PlayCardUseCase playCardUseCase;

    @Autowired
    PlayParadoxResolutionCardUseCase playParadoxResolutionCardUseCase;

    @Autowired
    ActionRoundRepository actionRoundRepository;

    @Autowired
    PlayerStateRepository playerStateRepository;

    @Autowired
    ParadoxResolutionPhaseRepository paradoxResolutionPhaseRepository;

    @Autowired
    FutureEventDefinitionPort futureEventDefinitionPort;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void concurrentParadoxSubmissionsFromSamePlayerConsumeExactlyOneCard() throws Exception {
        var gameId = UUID.randomUUID();
        var playerId = UUID.randomUUID();
        var firstCard = UUID.randomUUID();
        var secondCard = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();
        var targetOutcomeId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(_ -> {
            paradoxResolutionPhaseRepository.save(
                    new ParadoxResolutionPhase(UUID.randomUUID(), gameId, ERA, Instant.parse("2099-01-01T00:01:00Z")));
            var playerState = new PlayerState(UUID.randomUUID(), gameId, playerId);
            playerState.dealCard(new PlayerState.CardInstance(firstCard, CardType.PUSH), 5);
            playerState.dealCard(new PlayerState.CardInstance(secondCard, CardType.STABILIZE), 5);
            playerStateRepository.save(playerState);
            futureEventDefinitionPort.replaceForGameEra(
                    gameId,
                    ERA,
                    List.of(new FutureEventDefinitionPort.EventDefinition(
                            targetEventId,
                            List.of(new FutureEventDefinitionPort.OutcomeDefinition(targetOutcomeId, 50)))));
        });

        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(
                    () -> submitParadoxCard(barrier, gameId, playerId, firstCard, targetEventId, targetOutcomeId));
            Future<Object> second = executor.submit(
                    () -> submitParadoxCard(barrier, gameId, playerId, secondCard, targetEventId, targetOutcomeId));

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .filteredOn(PlayParadoxResolutionCardUseCase.Result.class::isInstance)
                    .hasSize(1);
            assertThat(List.of(first.get(), second.get()))
                    .filteredOn(DuplicateParadoxResolutionSubmissionException.class::isInstance)
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        var playerState =
                playerStateRepository.findByGameIdAndPlayerId(gameId, playerId).orElseThrow();
        assertThat(playerState.hand()).hasSize(1);
        assertThat(paradoxResolutionPhaseRepository
                        .findByGameIdAndEraNumber(gameId, ERA)
                        .orElseThrow()
                        .submittedPlayerIds())
                .containsExactly(playerId);
    }

    @Test
    @DisplayName("two players submitting into the same round at once both persist — no lost update")
    void concurrentSubmissions_bothCardsPersist_andRoundReachesAllSubmitted() throws Exception {
        var gameId = UUID.randomUUID();
        var playerA = UUID.randomUUID();
        var playerB = UUID.randomUUID();
        var cardA = UUID.randomUUID();
        var cardB = UUID.randomUUID();
        var targetEventId = UUID.randomUUID();

        var roundId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(_ -> {
            actionRoundRepository.save(new ActionRound(
                    roundId, new ActionRoundConfig(gameId, ERA, ROUND, TIMER_SECONDS), List.of(playerA, playerB)));
            playerStateRepository.save(playerStateWithCard(gameId, playerA, cardA));
            playerStateRepository.save(playerStateWithCard(gameId, playerB, cardB));
            futureEventDefinitionPort.replaceForGameEra(
                    gameId, ERA, List.of(new FutureEventDefinitionPort.EventDefinition(targetEventId, List.of())));
        });

        // Line both submissions up on a barrier so they hit the round in the same instant.
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<PlayCardUseCase.Result> submitA = executor.submit(() -> {
                barrier.await();
                return playCardUseCase.handle(command(gameId, playerA, cardA, targetEventId));
            });
            Future<PlayCardUseCase.Result> submitB = executor.submit(() -> {
                barrier.await();
                return playCardUseCase.handle(command(gameId, playerB, cardB, targetEventId));
            });

            var resultA = submitA.get(30, TimeUnit.SECONDS);
            var resultB = submitB.get(30, TimeUnit.SECONDS);

            // Exactly one submission observes the emptied pending list; the other still saw a peer pending.
            assertThat(resultA.roundClosed() ^ resultB.roundClosed())
                    .as("exactly one submission should report the round fully submitted")
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }

        var reloaded = actionRoundRepository.findById(roundId).orElseThrow();
        var submittedCardIds = reloaded.submittedActions().stream()
                .filter(SubmittedAction.CardAction.class::isInstance)
                .map(action -> ((SubmittedAction.CardAction) action).cardInstanceId())
                .toList();

        assertThat(submittedCardIds).as("both cards must survive the race").containsExactlyInAnyOrder(cardA, cardB);
        assertThat(reloaded.pendingPlayerIds())
                .as("no player should remain pending once both have submitted")
                .isEmpty();
    }

    @Test
    @DisplayName("locked round read blocks a second transaction until the first commits")
    void lockedRead_serializesConcurrentTransactions() throws Exception {
        var gameId = UUID.randomUUID();
        var roundId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(_ -> actionRoundRepository.save(new ActionRound(
                roundId, new ActionRoundConfig(gameId, ERA, ROUND, TIMER_SECONDS), List.of(UUID.randomUUID()))));

        var lockAcquiredByFirst = new CountDownLatch(1);
        var secondAttempting = new CountDownLatch(1);
        var releaseLock = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(_ -> {
                actionRoundRepository
                        .findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, ERA, ROUND)
                        .orElseThrow();
                lockAcquiredByFirst.countDown();
                awaitLatch(releaseLock);
            }));

            Future<?> second = executor.submit(() -> {
                lockAcquiredByFirst.await();
                secondAttempting.countDown();
                transactionTemplate.executeWithoutResult(_ -> actionRoundRepository
                        .findByGameIdAndEraNumberAndRoundNumberWithLock(gameId, ERA, ROUND)
                        .orElseThrow());
                return null;
            });

            secondAttempting.await();
            await().alias("second transaction should stay blocked while the first still holds the lock")
                    .during(Duration.ofMillis(300))
                    .atMost(Duration.ofMillis(600))
                    .until(() -> !second.isDone());

            releaseLock.countDown();
            first.get(30, TimeUnit.SECONDS);
            await().alias("second transaction should unblock once the first releases the lock")
                    .atMost(Duration.ofSeconds(5))
                    .until(second::isDone);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private PlayCardUseCase.Command command(UUID gameId, UUID playerId, UUID cardInstanceId, UUID targetEventId) {
        return new PlayCardUseCase.Command(gameId, ERA, ROUND, playerId, cardInstanceId, targetEventId, null, null);
    }

    private PlayerState playerStateWithCard(UUID gameId, UUID playerId, UUID cardInstanceId) {
        var state = new PlayerState(UUID.randomUUID(), gameId, playerId);
        state.dealCard(new PlayerState.CardInstance(cardInstanceId, CardType.PUSH), 5);
        return state;
    }

    private Object submitParadoxCard(
            CyclicBarrier barrier,
            UUID gameId,
            UUID playerId,
            UUID cardInstanceId,
            UUID targetEventId,
            UUID targetOutcomeId)
            throws Exception {
        barrier.await();
        try {
            return playParadoxResolutionCardUseCase.handle(new PlayParadoxResolutionCardUseCase.Command(
                    gameId, ERA, playerId, cardInstanceId, targetEventId, targetOutcomeId));
        } catch (DuplicateParadoxResolutionSubmissionException ex) {
            return ex;
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for latch", e);
        }
    }
}
