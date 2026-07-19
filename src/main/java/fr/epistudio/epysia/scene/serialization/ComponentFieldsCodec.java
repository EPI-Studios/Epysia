package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.assets.AssetRef;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ExportedProperty;
import fr.epistudio.epysia.reflection.Reflection;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ComponentFieldsCodec {

    interface ReferenceSink {

        ReferenceSink IGNORING = new ReferenceSink() {
            @Override
            public void referenceByIndex(ExportedProperty property, int index) {
            }

            @Override
            public void referenceById(ExportedProperty property, String id) {
            }
        };

        void referenceByIndex(ExportedProperty property, int index);

        void referenceById(ExportedProperty property, String id);
    }

    void writeFields(JsonWriter writer, IComponent component, Function<GameObject, String> referenceEncoder) {
        for (ExportedProperty property : Reflection.scan(component)) {
            writeProperty(writer, property, referenceEncoder);
        }
    }

    private void writeProperty(JsonWriter writer, ExportedProperty property,
                               Function<GameObject, String> referenceEncoder) {
        writer.key(property.fieldName());
        Object value = property.read();
        switch (property.kind()) {
            case FLOAT -> writer.valueNumber((float) value);
            case INT -> writer.valueNumber((int) value);
            case BOOLEAN -> writer.valueBoolean((boolean) value);
            case STRING -> writer.valueString((String) value);
            case VECTOR3 -> writeVector3(writer, (Vector3f) value);
            case QUATERNION -> writeQuaternion(writer, (Quaternionf) value);
            case ENUM -> writer.valueString(value == null ? "" : ((Enum<?>) value).name());
            case ASSET_REF -> writeAssetRef(writer, value);
            case GAMEOBJECT_REF -> writeGameObjectReference(writer, value, referenceEncoder);
            default -> writer.valueString("(unsupported)");
        }
    }

    private static void writeAssetRef(JsonWriter writer, Object value) {
        if (!(value instanceof AssetRef<?> reference)) {
            writer.valueString("");
            return;
        }
        writer.beginObject();
        writer.key("guid").valueString(reference.guid());
        writer.key("path").valueString(reference.path());
        writer.endObject();
    }

    private static void writeGameObjectReference(JsonWriter writer, Object value,
                                                 Function<GameObject, String> referenceEncoder) {
        if (value instanceof GameObject target) {
            writer.valueString(referenceEncoder.apply(target));
        } else {
            writer.valueString("");
        }
    }

    private static void writeVector3(JsonWriter writer, Vector3f vector) {
        writer.beginArray();
        writer.valueNumber(vector.x);
        writer.valueNumber(vector.y);
        writer.valueNumber(vector.z);
        writer.endArray();
    }

    private static void writeQuaternion(JsonWriter writer, Quaternionf rotation) {
        writer.beginArray();
        writer.valueNumber(rotation.x);
        writer.valueNumber(rotation.y);
        writer.valueNumber(rotation.z);
        writer.valueNumber(rotation.w);
        writer.endArray();
    }

    void applyFields(IComponent component, Map<String, Object> fields, ReferenceSink referenceSink) {
        for (ExportedProperty property : Reflection.scan(component)) {
            Object value = fields.get(property.fieldName());
            if (value == null) {
                continue;
            }
            applyProperty(property, value, referenceSink);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyProperty(ExportedProperty property, Object value, ReferenceSink referenceSink) {
        switch (property.kind()) {
            case FLOAT -> property.writeFloat(asFloat(value));
            case INT -> property.writeInt((int) asFloat(value));
            case BOOLEAN -> property.writeBoolean(value instanceof Boolean booleanValue && booleanValue);
            case STRING -> property.writeObject(value.toString());
            case VECTOR3 -> applyVector3(property, (List<Object>) value);
            case QUATERNION -> applyQuaternion(property, (List<Object>) value);
            case ENUM -> applyEnum(property, value);
            case ASSET_REF -> applyAssetRef(property, value);
            case GAMEOBJECT_REF -> applyGameObjectReference(property, value, referenceSink);
            default -> {
            }
        }
    }

    private static void applyVector3(ExportedProperty property, List<Object> values) {
        Vector3f target = (Vector3f) property.read();
        target.set(asFloat(values.get(0)), asFloat(values.get(1)), asFloat(values.get(2)));
    }

    private static void applyQuaternion(ExportedProperty property, List<Object> values) {
        Quaternionf target = (Quaternionf) property.read();
        target.set(asFloat(values.get(0)), asFloat(values.get(1)),
                asFloat(values.get(2)), asFloat(values.get(3)));
    }

    private static void applyGameObjectReference(ExportedProperty property, Object value,
                                                 ReferenceSink referenceSink) {
        switch (value) {
            case GameObject target -> property.writeObject(target);
            case Number index when index.intValue() >= 0 ->
                    referenceSink.referenceByIndex(property, index.intValue());
            case String id when !id.isEmpty() -> referenceSink.referenceById(property, id);
            default -> {
            }
        }
    }

    private static void applyAssetRef(ExportedProperty property, Object value) {
        if (!(property.read() instanceof AssetRef<?> reference)) {
            return;
        }
        switch (value) {
            case String path -> reference.setPath(path);
            case Map<?, ?> object -> {
                if (object.get("guid") instanceof String guid) {
                    reference.setGuid(guid);
                }
                if (object.get("path") instanceof String path) {
                    reference.setPath(path);
                }
            }
            default -> {
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyEnum(ExportedProperty property, Object value) {
        if (!(value instanceof String name) || name.isEmpty()) {
            return;
        }
        Class<?> type = property.fieldType();
        if (!type.isEnum()) {
            return;
        }
        try {
            Enum<?> constant = Enum.valueOf((Class<Enum>) type, name);
            property.writeObject(constant);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static float asFloat(Object number) {
        if (number instanceof Number numericValue) {
            return numericValue.floatValue();
        }
        return 0.0f;
    }
}
