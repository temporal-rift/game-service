package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.dao.DuplicateKeyException;

/**
 * One table of an {@link InMemoryDatabase}. Rows are stored by reference and treated as immutable: callers map a
 * changed aggregate to a fresh row and {@link #save} it. Reads inside a transaction see its own writes; lock-taking
 * reads wait for a competing transaction and then observe what it committed.
 */
public final class InMemoryTable<K, R> {

    private static final Object DELETED = new Object();

    private final InMemoryDatabase database;
    private final TableDefinition<K, R> definition;
    private final Map<K, R> committed = new LinkedHashMap<>();
    private final List<Map<Object, K>> committedUniques = new ArrayList<>();

    InMemoryTable(InMemoryDatabase database, TableDefinition<K, R> definition) {
        this.database = database;
        this.definition = definition;
        definition.uniques().forEach(unique -> committedUniques.add(new HashMap<>()));
    }

    public Optional<R> find(K key) {
        return database.read(() -> Optional.ofNullable(visibleIn(database.current(), key)));
    }

    /** Rows matching the filter in storage order: committed rows first, then this transaction's inserts. */
    public List<R> findAll(Predicate<? super R> filter) {
        return database.read(() -> visibleRows().stream().filter(filter).toList());
    }

    public Optional<R> findForUpdate(K key) {
        database.lock(database.requireTransaction(), rowLock(key));
        return find(key);
    }

    /** Locks the matching rows in key order, then returns those that still match, sorted by the given order. */
    public List<R> findAllForUpdate(Predicate<? super R> filter, Comparator<? super R> order) {
        var transaction = database.requireTransaction();
        var keys = findAll(filter).stream()
                .map(definition.primaryKey())
                .sorted(Comparator.comparing(Object::toString))
                .toList();
        var locked = new ArrayList<R>();
        for (var key : keys) {
            database.lock(transaction, rowLock(key));
            find(key).filter(filter).ifPresent(locked::add);
        }
        locked.sort(order);
        return locked;
    }

    /** Inserts or replaces the row with its primary key; another row holding a unique value is a conflict. */
    public void save(R row) {
        database.write(transaction -> {
            lockForWrite(transaction, row);
            var conflict = conflictingKey(row, transaction);
            if (conflict != null) {
                throw new DuplicateKeyException(
                        "Unique constraint of table " + definition.name() + " violated by row " + conflict);
            }
            transaction.overlay(this).put(definition.primaryKey().apply(row), row);
            return null;
        });
    }

    /** Inserts the row unless its primary key or any unique value is taken; reports whether it was inserted. */
    public boolean insertIfAbsent(R row) {
        return database.write(transaction -> {
            lockForWrite(transaction, row);
            var key = definition.primaryKey().apply(row);
            if (database.read(() -> visibleIn(transaction, key)) != null || conflictingKey(row, transaction) != null) {
                return false;
            }
            transaction.overlay(this).put(key, row);
            return true;
        });
    }

    public void delete(K key) {
        database.write(transaction -> {
            database.lock(transaction, rowLock(key));
            transaction.overlay(this).put(key, DELETED);
            return null;
        });
    }

    public int deleteAll(Predicate<? super R> filter) {
        return database.write(transaction -> {
            var keys = findAll(filter).stream().map(definition.primaryKey()).toList();
            keys.forEach(key -> {
                database.lock(transaction, rowLock(key));
                transaction.overlay(this).put(key, DELETED);
            });
            return keys.size();
        });
    }

    void forgetReplaced(Map<Object, Object> writes) {
        writes.keySet().forEach(key -> {
            @SuppressWarnings("unchecked")
            var previous = committed.get((K) key);
            if (previous != null) {
                forgetUniques(previous);
            }
        });
    }

    @SuppressWarnings("unchecked")
    void applyCommitted(Map<Object, Object> writes) {
        writes.forEach((key, written) -> {
            if (written == DELETED) {
                committed.remove((K) key);
            } else {
                committed.put((K) key, (R) written);
                rememberUniques((R) written);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private R visibleIn(InMemoryTransaction transaction, K key) {
        var overlay = transaction == null ? null : transaction.overlayIfPresent(this);
        if (overlay != null && overlay.containsKey(key)) {
            var written = overlay.get(key);
            return written == DELETED ? null : (R) written;
        }
        return committed.get(key);
    }

    @SuppressWarnings("unchecked")
    private List<R> visibleRows() {
        var transaction = database.current();
        var overlay = transaction == null ? null : transaction.overlayIfPresent(this);
        if (overlay == null) {
            return new ArrayList<>(committed.values());
        }
        var rows = new ArrayList<R>();
        committed.forEach((key, row) -> {
            if (!overlay.containsKey(key)) {
                rows.add(row);
            } else if (overlay.get(key) != DELETED) {
                rows.add((R) overlay.get(key));
            }
        });
        overlay.forEach((key, written) -> {
            if (written != DELETED && !committed.containsKey(key)) {
                rows.add((R) written);
            }
        });
        return rows;
    }

    private void lockForWrite(InMemoryTransaction transaction, R row) {
        database.lock(transaction, rowLock(definition.primaryKey().apply(row)));
        definition.uniques().forEach(unique -> {
            var value = unique.extractor().apply(row);
            if (value != null) {
                database.lock(transaction, new UniqueLock(definition.name(), unique.name(), value));
            }
        });
    }

    /** The primary key of another visible row holding one of this row's unique values, or {@code null}. */
    @SuppressWarnings("unchecked")
    private K conflictingKey(R row, InMemoryTransaction transaction) {
        var key = definition.primaryKey().apply(row);
        var overlay = transaction.overlayIfPresent(this);
        for (var i = 0; i < definition.uniques().size(); i++) {
            var extractor = definition.uniques().get(i).extractor();
            var value = extractor.apply(row);
            if (value == null) {
                continue;
            }
            if (overlay != null) {
                for (var entry : overlay.entrySet()) {
                    if (!entry.getKey().equals(key)
                            && entry.getValue() != DELETED
                            && value.equals(extractor.apply((R) entry.getValue()))) {
                        return entry.getKey();
                    }
                }
            }
            var index = committedUniques.get(i);
            var owner = database.read(() -> index.get(value));
            if (owner != null && !owner.equals(key) && (overlay == null || !overlay.containsKey(owner))) {
                return owner;
            }
        }
        return null;
    }

    private void rememberUniques(R row) {
        var key = definition.primaryKey().apply(row);
        for (var i = 0; i < definition.uniques().size(); i++) {
            var value = definition.uniques().get(i).extractor().apply(row);
            if (value != null) {
                committedUniques.get(i).put(value, key);
            }
        }
    }

    private void forgetUniques(R row) {
        for (var i = 0; i < definition.uniques().size(); i++) {
            var value = definition.uniques().get(i).extractor().apply(row);
            if (value != null) {
                committedUniques.get(i).remove(value);
            }
        }
    }

    private RowLock rowLock(Object key) {
        return new RowLock(definition.name(), key);
    }

    private record RowLock(String table, Object key) {}

    private record UniqueLock(String table, String constraint, Object value) {}
}
