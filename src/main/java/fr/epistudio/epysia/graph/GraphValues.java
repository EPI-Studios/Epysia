package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.gameobjects.GameObject;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class GraphValues {

    public static final Object ABSENT = new Object() {
        @Override
        public String toString() {
            return "";
        }
    };

    private GraphValues() {
    }

    public static Object coerce(Object value, PinType target) {
        return switch (target) {
            case FLOAT, NUMERIC -> asFloat(value);
            case INT -> asInt(value);
            case BOOLEAN -> asBoolean(value);
            case STRING -> asString(value);
            case VECTOR2 -> asVector2(value);
            case VECTOR3 -> asVector(value);
            case VECTOR4 -> asVector4(value);
            case GAME_OBJECT -> value instanceof GameObject ? value : ABSENT;
            case OBJECT, EXEC -> value == null ? ABSENT : value;
        };
    }

    public static Object defaultFor(PinType type) {
        return coerce(ABSENT, type);
    }

    public static float asFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    public static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    public static boolean asBoolean(Object value) {
        return value instanceof Boolean flag && flag;
    }

    public static String asString(Object value) {
        if (value == null || value == ABSENT) {
            return "";
        }
        return String.valueOf(value);
    }

    public static Vector3f asVector(Object value) {
        if (value instanceof Vector3f vector) {
            return new Vector3f(vector);
        }
        if (value instanceof List<?> list && list.size() == 3) {
            return new Vector3f(asFloat(list.get(0)), asFloat(list.get(1)), asFloat(list.get(2)));
        }
        return new Vector3f();
    }

    public static Vector2f asVector2(Object value) {
        if (value instanceof Vector2f vector) {
            return new Vector2f(vector);
        }
        if (value instanceof List<?> list && list.size() == 2) {
            return new Vector2f(asFloat(list.get(0)), asFloat(list.get(1)));
        }
        return new Vector2f();
    }

    public static Vector4f asVector4(Object value) {
        if (value instanceof Vector4f vector) {
            return new Vector4f(vector);
        }
        if (value instanceof List<?> list && list.size() == 4) {
            return new Vector4f(asFloat(list.get(0)), asFloat(list.get(1)),
                    asFloat(list.get(2)), asFloat(list.get(3)));
        }
        return new Vector4f();
    }
}
