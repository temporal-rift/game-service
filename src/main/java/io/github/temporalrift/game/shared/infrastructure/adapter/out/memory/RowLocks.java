package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.dao.CannotAcquireLockException;

/** Transaction-scoped exclusive locks: a second transaction asking for a held key waits for the owner to finish. */
final class RowLocks {

    private final Map<Object, InMemoryTransaction> owners = new HashMap<>();
    private final Duration timeout;

    RowLocks(Duration timeout) {
        this.timeout = timeout;
    }

    synchronized void acquire(InMemoryTransaction transaction, Object key) {
        var deadline = System.nanoTime() + timeout.toNanos();
        try {
            for (var owner = owners.putIfAbsent(key, transaction);
                    owner != transaction;
                    owner = owners.putIfAbsent(key, transaction)) {
                if (owner == null) {
                    transaction.heldLocks().add(key);
                    return;
                }
                if (wouldDeadlock(transaction, owner)) {
                    throw new CannotAcquireLockException("Deadlock detected while locking " + key);
                }
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new CannotAcquireLockException(
                            "Timed out after " + timeout + " waiting for the lock on " + key);
                }
                transaction.waitingFor(owner);
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CannotAcquireLockException("Interrupted while waiting for the lock on " + key, e);
        } finally {
            transaction.waitingFor(null);
        }
    }

    synchronized void releaseAll(InMemoryTransaction transaction) {
        transaction.heldLocks().forEach(owners::remove);
        transaction.heldLocks().clear();
        notifyAll();
    }

    private static boolean wouldDeadlock(InMemoryTransaction waiter, InMemoryTransaction owner) {
        for (var next = owner; next != null; next = next.waitingFor()) {
            if (next == waiter) {
                return true;
            }
        }
        return false;
    }
}
