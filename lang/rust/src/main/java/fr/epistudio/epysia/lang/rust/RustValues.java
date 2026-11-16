package fr.epistudio.epysia.lang.rust;

import fr.epistudio.epysia.scene.serialization.JsonWriter;

final class RustValues {

    private RustValues() {
    }

    static Class<?> typeOf(String kind) {
        return switch (kind) {
            case "bool" -> boolean.class;
            case "i32" -> int.class;
            case "f32" -> float.class;
            default -> String.class;
        };
    }

    static Object toJava(Object jsonValue, Class<?> type) {
        if (jsonValue == null) {
            return null;
        }
        if (type == float.class) {
            return jsonValue instanceof Number number ? number.floatValue() : 0.0f;
        }
        if (type == int.class) {
            return jsonValue instanceof Number number ? number.intValue() : 0;
        }
        if (type == boolean.class) {
            return jsonValue instanceof Boolean flag ? flag : Boolean.FALSE;
        }
        return String.valueOf(jsonValue);
    }

    static String encode(Object value, Class<?> type) {
        JsonWriter writer = new JsonWriter();
        if (value == null) {
            writer.valueNull();
        } else if (type == float.class) {
            writer.valueNumber(((Number) value).floatValue());
        } else if (type == int.class) {
            writer.valueNumber(((Number) value).intValue());
        } else if (type == boolean.class) {
            writer.valueBoolean((Boolean) value);
        } else {
            writer.valueString(String.valueOf(value));
        }
        return writer.toString();
    }
}
