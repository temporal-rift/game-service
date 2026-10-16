package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class InMemoryTransactionTest {

    record Row(int id, String name, String slot) {}

    private static final TableDefinition<Integer, Row> ROWS =
            TableDefinition.<Integer, Row>of("rows", Row::id).unique("slot", Row::slot);

    private InMemoryDatabase database;
    private InMemoryTable<Integer, Row> rows;
    private TransactionTemplate transactions;
    private TransactionTemplate requiresNew;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        database = new InMemoryDatabase(Duration.ofSeconds(5));
        rows = database.table(ROWS);
        var manager = new InMemoryTransactionManager(database);
        transactions = new TransactionTemplate(manager);
        requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void committedWritesAreVisibleAfterwards() {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "a", null)));

        assertThat(rows.find(1)).contains(new Row(1, "a", null));
    }

    @Test
    void rollbackDiscardsEveryWrite() {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "kept", null)));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    rows.save(new Row(2, "lost", null));
                    rows.save(new Row(1, "changed", null));
                    rows.delete(1);
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows.findAll(row -> true)).containsExactly(new Row(1, "kept", null));
    }

    @Test
    void uncommittedWritesAreInvisibleToOtherTransactions() throws Exception {
        var written = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writer = CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(status -> {
                    rows.save(new Row(1, "pending", null));
                    written.countDown();
                    awaitLatch(release);
                }),
                executor);
        assertThat(written.await(5, TimeUnit.SECONDS)).isTrue();

        Optional<Row> seen = transactions.execute(status -> rows.find(1));
        assertThat(seen).isEmpty();

        release.countDown();
        writer.get(5, TimeUnit.SECONDS);
        assertThat(rows.find(1)).isPresent();
    }

    @Test
    void aTransactionSeesItsOwnWrites() {
        transactions.executeWithoutResult(status -> {
            rows.save(new Row(1, "a", null));
            assertThat(rows.find(1)).isPresent();
            assertThat(rows.findAll(row -> true)).hasSize(1);
            rows.delete(1);
            assertThat(rows.find(1)).isEmpty();
        });
    }

    @Test
    void requiresNewCommitsIndependentlyOfTheSuspendedTransaction() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    rows.save(new Row(1, "outer", null));
                    requiresNew.executeWithoutResult(inner -> {
                        assertThat(rows.find(1)).isEmpty();
                        rows.save(new Row(2, "inner", null));
                    });
                    assertThat(rows.find(2)).isPresent();
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(rows.findAll(row -> true)).containsExactly(new Row(2, "inner", null));
    }

    @Test
    void afterCommitCallbacksRunOnlyOnCommit() {
        var calls = new AtomicInteger();
        TransactionSynchronization afterCommit = new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                calls.incrementAndGet();
            }
        };

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    TransactionSynchronizationManager.registerSynchronization(afterCommit);
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(0);

        transactions.executeWithoutResult(
                status -> TransactionSynchronizationManager.registerSynchronization(afterCommit));
        assertThat(calls).hasValue(1);
    }

    @Test
    void rollbackOnlyParticipationRollsTheWholeTransactionBack() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                    rows.save(new Row(1, "a", null));
                    try {
                        transactions.executeWithoutResult(inner -> {
                            throw new IllegalStateException("inner");
                        });
                    } catch (IllegalStateException _) {
                        // The participating failure already marked the shared transaction rollback-only.
                    }
                }))
                .hasMessageContaining("rolled back");

        assertThat(rows.find(1)).isEmpty();
    }

    @Test
    void aHeldRowLockBlocksACompetitorUntilCommit() throws Exception {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "start", null)));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondEntered = new AtomicBoolean();
        var first = CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(status -> {
                    rows.findForUpdate(1);
                    locked.countDown();
                    awaitLatch(release);
                    rows.save(new Row(1, "first", null));
                }),
                executor);
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        var second = CompletableFuture.supplyAsync(
                () -> transactions.execute(status -> {
                    secondEntered.set(true);
                    return rows.findForUpdate(1).orElseThrow();
                }),
                executor);

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).until(() -> !second.isDone());
        assertThat(secondEntered).isTrue();

        release.countDown();
        first.get(5, TimeUnit.SECONDS);
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(new Row(1, "first", null));
    }

    @Test
    void aRolledBackLockHolderReleasesTheCompetitor() throws Exception {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "start", null)));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = CompletableFuture.runAsync(
                () -> assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                            rows.findForUpdate(1);
                            locked.countDown();
                            awaitLatch(release);
                            throw new IllegalStateException("boom");
                        }))
                        .isInstanceOf(IllegalStateException.class),
                executor);
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
        var second = CompletableFuture.supplyAsync(
                () -> transactions.execute(status -> rows.findForUpdate(1).orElseThrow()), executor);

        release.countDown();
        first.get(5, TimeUnit.SECONDS);

        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(new Row(1, "start", null));
    }

    @Test
    void lockingTwiceInOneTransactionDoesNotBlock() {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "a", null)));

        transactions.executeWithoutResult(status -> {
            rows.findForUpdate(1);
            assertThat(rows.findForUpdate(1)).isPresent();
        });
    }

    @Test
    void lockingWithoutATransactionFails() {
        assertThatThrownBy(() -> rows.findForUpdate(1)).isInstanceOf(CannotAcquireLockException.class);
    }

    @Test
    void aLockTimeoutFailsInsteadOfHanging() throws Exception {
        var shortLived = new InMemoryDatabase(Duration.ofMillis(200));
        var table = shortLived.table(ROWS);
        var template = new TransactionTemplate(new InMemoryTransactionManager(shortLived));
        template.executeWithoutResult(status -> table.save(new Row(1, "a", null)));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = CompletableFuture.runAsync(
                () -> template.executeWithoutResult(status -> {
                    table.findForUpdate(1);
                    locked.countDown();
                    awaitLatch(release);
                }),
                executor);
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> template.executeWithoutResult(status -> table.findForUpdate(1)))
                .isInstanceOf(CannotAcquireLockException.class);

        release.countDown();
        holder.get(5, TimeUnit.SECONDS);
    }

    @Test
    void crossedLocksAreReportedAsADeadlock() throws Exception {
        transactions.executeWithoutResult(status -> {
            rows.save(new Row(1, "a", null));
            rows.save(new Row(2, "b", null));
        });
        var firstLocked = new CountDownLatch(1);
        var secondLocked = new CountDownLatch(1);
        var first = CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(status -> {
                    rows.findForUpdate(1);
                    firstLocked.countDown();
                    awaitLatch(secondLocked);
                    rows.findForUpdate(2);
                }),
                executor);
        var second = CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(status -> {
                    awaitLatch(firstLocked);
                    rows.findForUpdate(2);
                    secondLocked.countDown();
                    rows.findForUpdate(1);
                }),
                executor);

        var outcomes = List.of(first, second);
        await().atMost(Duration.ofSeconds(5)).until(() -> outcomes.stream().allMatch(CompletableFuture::isDone));
        assertThat(outcomes.stream().filter(CompletableFuture::isCompletedExceptionally))
                .hasSize(1);
    }

    @Test
    void insertIfAbsentReportsAnExistingKeyAndChangesNothing() {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "first", null)));

        var inserted = transactions.execute(status -> rows.insertIfAbsent(new Row(1, "second", null)));

        assertThat(inserted).isFalse();
        assertThat(rows.find(1)).contains(new Row(1, "first", null));
    }

    @Test
    void aConcurrentClaimWaitsForTheFirstClaimantAndThenLoses() throws Exception {
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(
                () -> transactions.execute(status -> {
                    var inserted = rows.insertIfAbsent(new Row(1, "first", null));
                    claimed.countDown();
                    awaitLatch(release);
                    return inserted;
                }),
                executor);
        assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
        var second = CompletableFuture.supplyAsync(
                () -> transactions.execute(status -> rows.insertIfAbsent(new Row(1, "second", null))), executor);

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).until(() -> !second.isDone());
        release.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(second.get(5, TimeUnit.SECONDS)).isFalse();
        assertThat(rows.find(1)).contains(new Row(1, "first", null));
    }

    @Test
    void aClaimSucceedsOnceTheFirstClaimantRollsBack() throws Exception {
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = CompletableFuture.runAsync(
                () -> assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                            rows.insertIfAbsent(new Row(1, "first", null));
                            claimed.countDown();
                            awaitLatch(release);
                            throw new IllegalStateException("boom");
                        }))
                        .isInstanceOf(IllegalStateException.class),
                executor);
        assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
        var second = CompletableFuture.supplyAsync(
                () -> transactions.execute(status -> rows.insertIfAbsent(new Row(1, "second", null))), executor);

        release.countDown();
        first.get(5, TimeUnit.SECONDS);

        assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(rows.find(1)).contains(new Row(1, "second", null));
    }

    @Test
    void aUniqueValueCannotBeHeldByTwoRows() {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "a", "slot")));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> rows.save(new Row(2, "b", "slot"))))
                .isInstanceOf(DuplicateKeyException.class);
        Boolean inserted = transactions.execute(status -> rows.insertIfAbsent(new Row(3, "c", "slot")));
        assertThat(inserted).isFalse();
        assertThat(rows.findAll(row -> true)).hasSize(1);
    }

    @Test
    void aFreedUniqueValueCanBeReused() {
        transactions.executeWithoutResult(status -> rows.save(new Row(1, "a", "slot")));

        transactions.executeWithoutResult(status -> {
            rows.delete(1);
            rows.save(new Row(2, "b", "slot"));
        });

        assertThat(rows.findAll(row -> true)).containsExactly(new Row(2, "b", "slot"));
    }

    @Test
    void nullUniqueValuesNeverConflict() {
        transactions.executeWithoutResult(status -> {
            rows.save(new Row(1, "a", null));
            rows.save(new Row(2, "b", null));
        });

        assertThat(rows.findAll(row -> true)).hasSize(2);
    }

    @Test
    void aWriteOutsideATransactionCommitsImmediately() {
        rows.save(new Row(1, "auto", null));

        assertThat(rows.find(1)).isPresent();
    }

    @Test
    void lockingQueriesReturnOnlyRowsThatStillMatch() {
        transactions.executeWithoutResult(status -> {
            rows.save(new Row(1, "x", null));
            rows.save(new Row(2, "y", null));
            rows.save(new Row(3, "x", null));
        });

        var locked = transactions.execute(status -> rows.findAllForUpdate(
                row -> "x".equals(row.name()), Comparator.comparing(Row::id).reversed()));

        assertThat(locked).extracting(Row::id).containsExactly(3, 1);
    }

    @Test
    void anAdvisoryLockBlocksUntilTheOwnerEnds() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(status -> {
                    database.lockUntilTransactionEnds("roster");
                    locked.countDown();
                    awaitLatch(release);
                }),
                executor);
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
        var second = CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(status -> database.lockUntilTransactionEnds("roster")),
                executor);

        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2)).until(() -> !second.isDone());
        release.countDown();

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
