package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Schema of one in-memory table: its name, how a row yields its primary key, and its other unique constraints.
 *
 * @param <K> primary key type
 * @param <R> row type; rows are stored as given and must not be mutated afterwards
 */
public record TableDefinition<K, R>(String name, Function<R, K> primaryKey, List<UniqueConstraint<R>> uniques) {

    public TableDefinition {
        uniques = List.copyOf(uniques);
    }

    public static <K, R> TableDefinition<K, R> of(String name, Function<R, K> primaryKey) {
        return new TableDefinition<>(name, primaryKey, List.of());
    }

    /** Adds a unique constraint over a value with value equality; rows whose value is {@code null} are exempt. */
    public TableDefinition<K, R> unique(String constraintName, Function<R, ?> extractor) {
        var all = new ArrayList<>(uniques);
        all.add(new UniqueConstraint<>(constraintName, extractor));
        return new TableDefinition<>(name, primaryKey, all);
    }

    /** A composite unique value, or {@code null} when any part is null, since SQL never treats nulls as equal. */
    public static Object key(Object... parts) {
        for (var part : parts) {
            if (part == null) {
                return null;
            }
        }
        return List.of(parts);
    }

    public record UniqueConstraint<R>(String name, Function<R, ?> extractor) {}
}
