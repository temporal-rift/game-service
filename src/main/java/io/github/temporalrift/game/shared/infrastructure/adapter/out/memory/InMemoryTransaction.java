package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The uncommitted writes and held locks of one in-memory transaction; confined to the thread that began it. */
final class InMemoryTransaction {

    private final Map<InMemoryTable<?, ?>, Map<Object, Object>> writes = new LinkedHashMap<>();
    private final Set<Object> heldLocks = new LinkedHashSet<>();
    private InMemoryTransaction waitingFor;
    private boolean rollbackOnly;

    @SuppressWarnings("unchecked")
    <K> Map<K, Object> overlay(InMemoryTable<K, ?> table) {
        return (Map<K, Object>) writes.computeIfAbsent(table, t -> new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    <K> Map<K, Object> overlayIfPresent(InMemoryTable<K, ?> table) {
        return (Map<K, Object>) writes.get(table);
    }

    Map<InMemoryTable<?, ?>, Map<Object, Object>> writes() {
        return writes;
    }

    Set<Object> heldLocks() {
        return heldLocks;
    }

    InMemoryTransaction waitingFor() {
        return waitingFor;
    }

    void waitingFor(InMemoryTransaction owner) {
        this.waitingFor = owner;
    }

    boolean rollbackOnly() {
        return rollbackOnly;
    }

    void markRollbackOnly() {
        this.rollbackOnly = true;
    }
}
