package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The state of one application instance's in-memory persistence: named tables of committed rows plus the lock table.
 * Transactions read committed rows and their own writes; their writes become visible to others atomically on commit.
 */
public class InMemoryDatabase {

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
        visibility.writeLock().lock();
        try {
            transaction.writes().forEach((table, rows) -> table.forgetReplaced(rows));
            transaction.writes().forEach((table, rows) -> table.applyCommitted(rows));
        } finally {
            visibility.writeLock().unlock();
            locks.releaseAll(transaction);
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
        try {
            var result = writer.apply(implicit);
            commit(implicit);
            return result;
        } catch (RuntimeException | Error e) {
            rollback(implicit);
            throw e;
        } finally {
            suspend();
        }
    }

    private record AdvisoryKey(Object key) {}
}
