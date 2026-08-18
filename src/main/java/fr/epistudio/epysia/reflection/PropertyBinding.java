package fr.epistudio.epysia.reflection;

import java.util.Optional;

public interface PropertyBinding {

    String name();

    Class<?> type();

    Object read();

    void write(Object value);

    default String label() {
        return "";
    }

    default float min() {
        return 0.0f;
    }

    default float max() {
        return 0.0f;
    }

    default float step() {
        return 0.0f;
    }

    default boolean color() {
        return false;
    }

    default boolean layerMask() {
        return false;
    }

    default boolean hidden() {
        return false;
    }

    default String[] assetExtensions() {
        return new String[0];
    }

    default Optional<Class<?>> elementType() {
        return Optional.empty();
    }
}
