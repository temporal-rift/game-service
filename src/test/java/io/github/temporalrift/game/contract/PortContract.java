package io.github.temporalrift.game.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A driven port's behavior contract. The same test methods run against the database adapter and the in-memory
 * adapter, so the two cannot drift: a contract only talks to the port, never to a database. Each test runs in a
 * rolled-back transaction; the annotation sits here because Spring resolves it from the interface declaring the test.
 */
@Transactional
public interface PortContract {

    ApplicationContext context();

    default <T> T port(Class<T> type) {
        return context().getBean(type);
    }

    default TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(context().getBean(PlatformTransactionManager.class));
    }

    default <T> T inNewTransaction(Supplier<T> work) {
        var template = new TransactionTemplate(context().getBean(PlatformTransactionManager.class));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> work.get());
    }

    default void inNewTransaction(Runnable work) {
        inNewTransaction(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Asserts that a second transaction running {@code lock} waits until the first one, which acquired it, ends. The
     * locked row must already be committed, as a database lock on a missing row locks nothing.
     */
    default void assertSecondLockerWaitsForFirst(Runnable lock) {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondAcquired = new AtomicBoolean();
        var first = CompletableFuture.runAsync(() -> inNewTransaction(() -> {
            lock.run();
            locked.countDown();
            awaitLatch(release);
        }));
        awaitLatch(locked);
        var second = CompletableFuture.runAsync(() -> inNewTransaction(() -> {
            lock.run();
            secondAcquired.set(true);
        }));

        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).until(() -> !secondAcquired.get());
        release.countDown();

        first.orTimeout(10, TimeUnit.SECONDS).join();
        second.orTimeout(10, TimeUnit.SECONDS).join();
        assertThat(secondAcquired).isTrue();
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
