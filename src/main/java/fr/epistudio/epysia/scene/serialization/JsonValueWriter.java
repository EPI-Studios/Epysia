package fr.epistudio.epysia.scene.serialization;

import java.util.List;
import java.util.Map;

public final class JsonValueWriter {

    private JsonValueWriter() {
    }

    public static void write(JsonWriter writer, Object value) {
        switch (value) {
            case Map<?, ?> map -> writeObject(writer, map);
            case List<?> list -> writeArray(writer, list);
            case Boolean flag -> writer.valueBoolean(flag);
            case Integer number -> writer.valueNumber(number.intValue());
            case Long number -> writer.valueNumber(number.longValue());
            case Number number -> writer.valueNumber(number.floatValue());
            case null -> writer.valueString("");
            default -> writer.valueString(String.valueOf(value));
        }
    }

    private static void writeObject(JsonWriter writer, Map<?, ?> map) {
        writer.beginObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            writer.key(String.valueOf(entry.getKey()));
            write(writer, entry.getValue());
        }
        writer.endObject();
    }

    private static void writeArray(JsonWriter writer, List<?> list) {
        writer.beginArray();
        for (Object element : list) {
            write(writer, element);
        }
        writer.endArray();
    }
}
