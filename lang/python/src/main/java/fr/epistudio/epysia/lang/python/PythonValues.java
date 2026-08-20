package fr.epistudio.epysia.lang.python;

import org.graalvm.polyglot.Value;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class PythonValues {

    private PythonValues() {
    }

    static Class<?> typeOf(Value defaultValue) {
        if (defaultValue.isHostObject()) {
            return hostTypeOf(defaultValue.asHostObject());
        }
        return switch (metaNameOf(defaultValue)) {
            case "bool" -> boolean.class;
            case "int" -> int.class;
            case "float" -> float.class;
            default -> String.class;
        };
    }

    private static String metaNameOf(Value value) {
        Value meta = value.getMetaObject();
        return meta == null ? "" : meta.getMetaSimpleName();
    }

    private static Class<?> hostTypeOf(Object host) {
        if (host instanceof Vector2f) {
            return Vector2f.class;
        }
        if (host instanceof Vector3f) {
            return Vector3f.class;
        }
        if (host instanceof Vector4f) {
            return Vector4f.class;
        }
        return String.class;
    }

    static Object toJava(Value value, Class<?> type) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (type == float.class) {
            return (float) value.asDouble();
        }
        if (type == int.class) {
            return value.asInt();
        }
        if (type == boolean.class) {
            return value.asBoolean();
        }
        if (type == String.class) {
            return value.isString() ? value.asString() : value.toString();
        }
        return value.isHostObject() ? value.asHostObject() : null;
    }
}
