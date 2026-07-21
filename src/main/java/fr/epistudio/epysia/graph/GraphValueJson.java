package fr.epistudio.epysia.graph;

import fr.epistudio.epysia.scene.serialization.JsonWriter;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class GraphValueJson {

    private GraphValueJson() {
    }

    public static void write(JsonWriter writer, Object value) {
        switch (value) {
            case Vector2f vector -> writeComponents(writer, vector.x, vector.y);
            case Vector3f vector -> writeComponents(writer, vector.x, vector.y, vector.z);
            case Vector4f vector -> writeComponents(writer, vector.x, vector.y, vector.z, vector.w);
            case Boolean flag -> writer.valueBoolean(flag);
            case Integer number -> writer.valueNumber(number);
            case Long number -> writer.valueNumber(number.intValue());
            case Number number -> writer.valueNumber(number.floatValue());
            case null -> writer.valueString("");
            default -> writer.valueString(String.valueOf(value));
        }
    }

    private static void writeComponents(JsonWriter writer, float... components) {
        writer.beginArray();
        for (float component : components) {
            writer.valueNumber(component);
        }
        writer.endArray();
    }

    public static Object normalize(Object parsed) {
        return switch (parsed) {
            case Double number -> number.floatValue();
            case Long number -> number.intValue();
            case List<?> list when isNumberList(list, 2) -> GraphValues.asVector2(list);
            case List<?> list when isNumberList(list, 3) -> GraphValues.asVector(list);
            case List<?> list when isNumberList(list, 4) -> GraphValues.asVector4(list);
            case null -> "";
            default -> parsed;
        };
    }

    private static boolean isNumberList(List<?> list, int size) {
        if (list.size() != size) {
            return false;
        }
        for (Object element : list) {
            if (!(element instanceof Number)) {
                return false;
            }
        }
        return true;
    }
}
