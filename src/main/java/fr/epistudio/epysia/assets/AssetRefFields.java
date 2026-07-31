package fr.epistudio.epysia.assets;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AssetRefFields {

    private static final int MAXIMUM_DEPTH = 4;

    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private AssetRefFields() {
    }

    public static void releaseAll(Object holder) {
        releaseFrom(holder, new IdentityHashMap<>(), 0);
    }

    private static void releaseFrom(Object holder, Map<Object, Boolean> visited, int depth) {
        if (holder == null || depth > MAXIMUM_DEPTH || visited.put(holder, Boolean.TRUE) != null) {
            return;
        }
        for (Field field : fieldsOf(holder.getClass())) {
            releaseValue(read(holder, field), visited, depth + 1);
        }
    }

    private static void releaseValue(Object value, Map<Object, Boolean> visited, int depth) {
        if (value instanceof AssetRef<?> reference) {
            reference.release();
            return;
        }
        if (value instanceof Iterable<?> items) {
            for (Object item : items) {
                releaseValue(item, visited, depth + 1);
            }
            return;
        }
        if (value instanceof Map<?, ?> entries) {
            for (Object item : entries.values()) {
                releaseValue(item, visited, depth + 1);
            }
            return;
        }
        if (value != null && value.getClass().isRecord()) {
            releaseFrom(value, visited, depth);
        }
    }

    private static Object read(Object holder, Field field) {
        try {
            return field.get(holder);
        } catch (IllegalAccessException unreachable) {
            throw new IllegalStateException("Asset reference field became inaccessible: " + field, unreachable);
        }
    }

    private static List<Field> fieldsOf(Class<?> type) {
        return FIELDS.computeIfAbsent(type, AssetRefFields::collect);
    }

    private static List<Field> collect(Class<?> type) {
        List<Field> collected = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (mayHoldReferences(field.getType())) {
                    field.setAccessible(true);
                    collected.add(field);
                }
            }
        }
        return List.copyOf(collected);
    }

    private static boolean mayHoldReferences(Class<?> fieldType) {
        return fieldType == AssetRef.class
                || Iterable.class.isAssignableFrom(fieldType)
                || Map.class.isAssignableFrom(fieldType)
                || fieldType.isRecord();
    }
}
