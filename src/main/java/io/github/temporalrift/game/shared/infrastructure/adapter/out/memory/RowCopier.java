package io.github.temporalrift.game.shared.infrastructure.adapter.out.memory;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        if (value instanceof Record record) {
            return copyRecord(record, copies);
        }
        return copyObject(value, copies);
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

    private static Object copyRecord(Record record, Map<Object, Object> copies) {
        var components = record.getClass().getRecordComponents();
        var types = new Class<?>[components.length];
        var arguments = new Object[components.length];
        try {
            for (var i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                var accessor = components[i].getAccessor();
                accessor.setAccessible(true);
                arguments[i] = copy(accessor.invoke(record), copies);
            }
            var constructor = record.getClass().getDeclaredConstructor(types);
            constructor.setAccessible(true);
            var copy = constructor.newInstance(arguments);
            copies.put(record, copy);
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot copy record " + record.getClass().getName(), e);
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
