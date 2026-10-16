package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

/**
 * Transaction manager over an {@link InMemoryDatabase}. It relies on Spring's own synchronization machinery, so
 * {@code @Transactional}, {@code REQUIRES_NEW} suspension and after-commit callbacks behave as with a database.
 */
public class InMemoryTransactionManager extends AbstractPlatformTransactionManager {

    private final transient InMemoryDatabase database;

    public InMemoryTransactionManager(InMemoryDatabase database) {
        this.database = database;
    }

    @Override
    protected Object doGetTransaction() {
        return new TransactionHandle(database.current());
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((TransactionHandle) transaction).transaction != null;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        ((TransactionHandle) transaction).transaction = database.begin();
    }

    @Override
    protected Object doSuspend(Object transaction) {
        ((TransactionHandle) transaction).transaction = null;
        return database.suspend();
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        database.resume((InMemoryTransaction) suspendedResources);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        database.commit(((TransactionHandle) status.getTransaction()).transaction);
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        database.rollback(((TransactionHandle) status.getTransaction()).transaction);
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        ((TransactionHandle) status.getTransaction()).transaction.markRollbackOnly();
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        if (((TransactionHandle) transaction).transaction != null) {
            database.suspend();
        }
    }

    private static final class TransactionHandle implements SmartTransactionObject {

        private InMemoryTransaction transaction;

        private TransactionHandle(InMemoryTransaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public boolean isRollbackOnly() {
            return transaction != null && transaction.rollbackOnly();
        }

        @Override
        public void flush() {
            // Writes are applied at commit; there is nothing to flush.
        }
    }
}
