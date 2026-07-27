package fr.epistudio.epysia.assets;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AssetRefFields {

    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private AssetRefFields() {
    }

    public static void releaseAll(Object holder) {
        for (Field field : fieldsOf(holder.getClass())) {
            release(holder, field);
        }
    }

    private static void release(Object holder, Field field) {
        try {
            AssetRef<?> reference = (AssetRef<?>) field.get(holder);
            if (reference != null) {
                reference.release();
            }
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
                if (field.getType() == AssetRef.class) {
                    field.setAccessible(true);
                    collected.add(field);
                }
            }
        }
        return List.copyOf(collected);
    }
}
