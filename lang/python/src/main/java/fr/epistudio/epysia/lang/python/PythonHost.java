package fr.epistudio.epysia.lang.python;

import org.joml.Vector3f;

import java.util.Collection;
import java.util.Optional;

public final class PythonHost {

    private PythonHost() {
    }

    public static float toFloat(double value) {
        return (float) value;
    }

    public static int toInt(double value) {
        return (int) value;
    }

    public static boolean isVector3(Object value) {
        return value instanceof Vector3f;
    }

    public static boolean isOptional(Object value) {
        return value instanceof Optional;
    }

    public static boolean isCollection(Object value) {
        return value instanceof Collection;
    }

    public static Object unwrapOptional(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    public static String typeNameOf(Object value) {
        return value == null ? "None" : value.getClass().getSimpleName();
    }
}
