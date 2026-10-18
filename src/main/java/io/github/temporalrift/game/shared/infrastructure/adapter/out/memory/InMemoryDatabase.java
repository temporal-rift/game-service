package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The state of one application instance's in-memory persistence: named tables of committed rows plus the lock table.
 * Transactions read committed rows and their own writes; their writes become visible to others atomically on commit.
 */
public class InMemoryDatabase {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryDatabase.class);

    private final ReentrantReadWriteLock visibility = new ReentrantReadWriteLock();
    private final Map<String, InMemoryTable<?, ?>> tables = new LinkedHashMap<>();
    private final RowLocks locks;

    public InMemoryDatabase(Duration lockTimeout) {
        this.locks = new RowLocks(lockTimeout);
    }

    @SuppressWarnings("unchecked")
    public synchronized <K, R> InMemoryTable<K, R> table(TableDefinition<K, R> definition) {
        return (InMemoryTable<K, R>)
                tables.computeIfAbsent(definition.name(), name -> new InMemoryTable<>(this, definition));
    }

    /**
     * Takes a lock on an arbitrary key until the surrounding transaction ends, like a database advisory lock.
     *
     * @throws CannotAcquireLockException when no transaction is active or the lock cannot be obtained in time
     */
    public void lockUntilTransactionEnds(Object key) {
        locks.acquire(requireTransaction(), new AdvisoryKey(key));
    }

    InMemoryTransaction begin() {
        var transaction = new InMemoryTransaction();
        TransactionSynchronizationManager.bindResource(this, transaction);
        return transaction;
    }

    InMemoryTransaction suspend() {
        return (InMemoryTransaction) TransactionSynchronizationManager.unbindResource(this);
    }

    void resume(InMemoryTransaction transaction) {
        TransactionSynchronizationManager.bindResource(this, transaction);
    }

    InMemoryTransaction current() {
        return (InMemoryTransaction) TransactionSynchronizationManager.getResource(this);
    }

    InMemoryTransaction requireTransaction() {
        var current = current();
        if (current == null) {
            throw new CannotAcquireLockException("Locking requires an active transaction");
        }
        return current;
    }

    void commit(InMemoryTransaction transaction) {
        transaction.flushManaged();
        visibility.writeLock().lock();
        try {
            transaction.writes().forEach(InMemoryTable::forgetReplaced);
            transaction.writes().forEach(InMemoryTable::applyCommitted);
            transaction.commitHooks().forEach(InMemoryDatabase::runCommitHook);
        } finally {
            visibility.writeLock().unlock();
            locks.releaseAll(transaction);
        }
    }

    /**
     * Runs the work at the transaction's place in the commit order: after its writes are visible and before any later
     * commit, so hooks of concurrent transactions run in the order those transactions committed. A hook must be
     * brief and must not re-enter the database.
     *
     * @throws CannotAcquireLockException when no transaction is active
     */
    public void onCommit(Runnable hook) {
        var current = current();
        if (current == null) {
            throw new CannotAcquireLockException("A commit hook requires an active transaction");
        }
        current.onCommit(hook);
    }

    private static void runCommitHook(Runnable hook) {
        try {
            hook.run();
        } catch (RuntimeException e) {
            LOG.error("A commit hook failed after its transaction committed", e);
        }
    }

    void rollback(InMemoryTransaction transaction) {
        transaction.writes().clear();
        locks.releaseAll(transaction);
    }

    void lock(InMemoryTransaction transaction, Object key) {
        locks.acquire(transaction, key);
    }

    <T> T read(Supplier<T> reader) {
        visibility.readLock().lock();
        try {
            return reader.get();
        } finally {
            visibility.readLock().unlock();
        }
    }

    /** Runs the write in the active transaction, or in a transaction of its own when none is active. */
    <T> T write(Function<InMemoryTransaction, T> writer) {
        var current = current();
        if (current != null) {
            return writer.apply(current);
        }
        var implicit = begin();
        var committed = false;
        try {
            var result = writer.apply(implicit);
            commit(implicit);
            committed = true;
            return result;
        } finally {
            if (!committed) {
                rollback(implicit);
            }
            suspend();
        }
    }

    private record AdvisoryKey(Object key) {}
}
