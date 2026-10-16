package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Deep-copies persisted row objects, so that what an in-memory table hands out or keeps can never be changed through
 * a reference the caller still holds, as a database returns detached values.
 */
final class RowCopier {

    private RowCopier() {}

    @SuppressWarnings("unchecked")
    static <T> T copy(T row) {
        return (T) copy(row, new IdentityHashMap<>());
    }

    private static Object copy(Object value, Map<Object, Object> copies) {
        if (value == null || isImmutableLeaf(value)) {
            return value;
        }
        var existing = copies.get(value);
        if (existing != null) {
            return existing;
        }
        if (value.getClass().isArray()) {
            return copyArray(value, copies);
        }
        if (value instanceof Collection<?> collection) {
            return copyCollection(collection, copies);
        }
        if (value instanceof Map<?, ?> map) {
            return copyMap(map, copies);
        }
        if (value instanceof Record source) {
            return copyRecord(source, copies);
        }
        return copyObject(value, copies);
    }

    /** Whether two rows hold equal state, compared field by field as a persistence context compares loaded rows. */
    static boolean sameState(Object left, Object right) {
        return same(left, right, new IdentityHashMap<>());
    }

    private static boolean same(Object left, Object right, Map<Object, Object> visiting) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.getClass() != right.getClass()) {
            return false;
        }
        if (isImmutableLeaf(left)) {
            return left.equals(right);
        }
        if (visiting.get(left) == right) {
            return true;
        }
        visiting.put(left, right);
        if (left.getClass().isArray()) {
            return sameElements(
                    IntStream.range(0, Array.getLength(left))
                            .mapToObj(i -> Array.get(left, i))
                            .iterator(),
                    IntStream.range(0, Array.getLength(right))
                            .mapToObj(i -> Array.get(right, i))
                            .iterator(),
                    visiting);
        }
        if (left instanceof Collection<?> collection) {
            return collection.size() == ((Collection<?>) right).size()
                    && sameElements(collection.iterator(), ((Collection<?>) right).iterator(), visiting);
        }
        if (left instanceof Map<?, ?> map) {
            var other = (Map<?, ?>) right;
            return map.size() == other.size()
                    && sameElements(map.keySet().iterator(), other.keySet().iterator(), visiting)
                    && sameElements(map.values().iterator(), other.values().iterator(), visiting);
        }
        return sameFields(left, right, visiting);
    }

    private static boolean sameElements(Iterator<?> left, Iterator<?> right, Map<Object, Object> visiting) {
        while (left.hasNext() && right.hasNext()) {
            if (!same(left.next(), right.next(), visiting)) {
                return false;
            }
        }
        return left.hasNext() == right.hasNext();
    }

    private static boolean sameFields(Object left, Object right, Map<Object, Object> visiting) {
        try {
            for (Class<?> type = left.getClass(); type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        if (!same(field.get(left), field.get(right), visiting)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot compare " + left.getClass().getName(), e);
        }
    }

    private static boolean isImmutableLeaf(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof UUID
                || value instanceof Enum<?>
                || value instanceof Class<?>
                || value.getClass().getName().startsWith("java.time.");
    }

    private static Object copyArray(Object array, Map<Object, Object> copies) {
        var length = Array.getLength(array);
        var copy = Array.newInstance(array.getClass().getComponentType(), length);
        copies.put(array, copy);
        for (var i = 0; i < length; i++) {
            Array.set(copy, i, copy(Array.get(array, i), copies));
        }
        return copy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object copyCollection(Collection<?> collection, Map<Object, Object> copies) {
        Collection copy;
        if (collection instanceof Set<?>) {
            copy = new LinkedHashSet<>();
        } else if (collection instanceof List<?>) {
            copy = new ArrayList<>(collection.size());
        } else {
            throw unsupported(collection);
        }
        copies.put(collection, copy);
        collection.forEach(element -> copy.add(copy(element, copies)));
        return copy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object copyMap(Map<?, ?> map, Map<Object, Object> copies) {
        Map copy = new LinkedHashMap<>();
        copies.put(map, copy);
        map.forEach((key, entry) -> copy.put(copy(key, copies), copy(entry, copies)));
        return copy;
    }

    private static Object copyRecord(Record source, Map<Object, Object> copies) {
        var components = source.getClass().getRecordComponents();
        var types = new Class<?>[components.length];
        var arguments = new Object[components.length];
        try {
            for (var i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                var accessor = components[i].getAccessor();
                accessor.setAccessible(true);
                arguments[i] = copy(accessor.invoke(source), copies);
            }
            var constructor = source.getClass().getDeclaredConstructor(types);
            constructor.setAccessible(true);
            var copy = constructor.newInstance(arguments);
            copies.put(source, copy);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot copy record " + source.getClass().getName(), e);
        }
    }

    private static Object copyObject(Object value, Map<Object, Object> copies) {
        try {
            Constructor<?> constructor = value.getClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            var copy = constructor.newInstance();
            copies.put(value, copy);
            for (Class<?> type = value.getClass(); type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        field.set(copy, copy(field.get(value), copies));
                    }
                }
            }
            return copy;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    value.getClass().getName() + " needs a no-argument constructor to be stored in memory", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot copy " + value.getClass().getName(), e);
        }
    }

    private static IllegalStateException unsupported(Object value) {
        return new IllegalStateException("Cannot copy " + value.getClass().getName() + " into memory");
    }
}
