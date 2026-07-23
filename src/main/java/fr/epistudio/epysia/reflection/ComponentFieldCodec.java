package fr.epistudio.epysia.reflection;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.IComponent;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ComponentFieldCodec {

    private ComponentFieldCodec() {
    }

    public static Map<String, Object> capture(IComponent component) {
        Map<String, Object> fields = new HashMap<>();
        for (ExportedProperty property : Reflection.scan(component)) {
            fields.put(property.fieldName(), captureValue(property));
        }
        return fields;
    }

    private static Object captureValue(ExportedProperty property) {
        Object value = property.read();
        return switch (property.kind()) {
            case FLOAT, INT, BOOLEAN, STRING -> value;
            case VECTOR2 -> {
                Vector2f vector = (Vector2f) value;
                yield numbers(vector.x, vector.y);
            }
            case VECTOR3 -> {
                Vector3f vector = (Vector3f) value;
                yield numbers(vector.x, vector.y, vector.z);
            }
            case QUATERNION -> {
                Quaternionf rotation = (Quaternionf) value;
                yield numbers(rotation.x, rotation.y, rotation.z, rotation.w);
            }
            case ENUM -> value == null ? "" : ((Enum<?>) value).name();
            case ASSET_REF -> value == null ? "" : ((AssetRef<?>) value).path();
            case GAMEOBJECT_REF -> value;
            default -> null;
        };
    }

    private static List<Object> numbers(float... values) {
        List<Object> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(value);
        }
        return list;
    }
}
