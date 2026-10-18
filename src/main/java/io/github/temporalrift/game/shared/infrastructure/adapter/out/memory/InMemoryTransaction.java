package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The uncommitted writes and held locks of one in-memory transaction; confined to the thread that began it. */
final class InMemoryTransaction {

    private final Map<InMemoryTable<?, ?>, Map<Object, Object>> writes = new LinkedHashMap<>();
    private final Set<Object> heldLocks = new LinkedHashSet<>();
    private final List<Managed> managed = new ArrayList<>();
    private final List<Runnable> commitHooks = new ArrayList<>();
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

    /** Tracks a loaded row: {@code flush} writes it back when it changed, before reads and at commit. */
    void manage(InMemoryTable<?, ?> table, Object key, Runnable flush) {
        managed.add(new Managed(table, key, flush));
    }

    /** Registers work to run at this transaction's place in the commit order, once its writes are visible. */
    void onCommit(Runnable hook) {
        commitHooks.add(hook);
    }

    List<Runnable> commitHooks() {
        return commitHooks;
    }

    void flushManaged() {
        new ArrayList<>(managed).forEach(entry -> entry.flush().run());
    }

    void forget(InMemoryTable<?, ?> table, Object key) {
        managed.removeIf(entry -> entry.table() == table && entry.key().equals(key));
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

    private record Managed(InMemoryTable<?, ?> table, Object key, Runnable flush) {}

    boolean rollbackOnly() {
        return rollbackOnly;
    }

    void markRollbackOnly() {
        this.rollbackOnly = true;
    }
}
