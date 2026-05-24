package fr.epistudio.epysia.editor.serialization;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.EditorComponentRegistry;
import fr.epistudio.epysia.editor.reflection.EditorReflection;
import fr.epistudio.epysia.editor.reflection.ExportedProperty;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class SceneSerializer {

    private final EditorComponentRegistry componentRegistry;

    public SceneSerializer(EditorComponentRegistry componentRegistry) {
        this.componentRegistry = componentRegistry;
    }

    public void save(Scene scene, Path path) throws IOException {
        save(scene, path, gameObject -> true);
    }

    public void save(Scene scene, Path path, Predicate<GameObject> include) throws IOException {
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key("name").valueString(scene.name());
        writer.key("gameObjects").beginArray();
        for (GameObject gameObject : scene.gameObjects()) {
            if (include.test(gameObject)) {
                writeGameObject(writer, gameObject);
            }
        }
        writer.endArray();
        writer.endObject();
        Files.writeString(path, writer.toString());
    }

    private void writeGameObject(JsonWriter writer, GameObject gameObject) {
        writer.beginObject();
        writer.key("name").valueString(gameObject.name());
        writer.key("components").beginArray();
        for (EditorComponentRegistry.Entry entry : componentRegistry.entries()) {
            gameObject.getComponent(entry.componentClass()).ifPresent(component -> writeComponent(writer, entry, component));
        }
        writer.endArray();
        writer.endObject();
    }

    private void writeComponent(JsonWriter writer, EditorComponentRegistry.Entry entry, IComponent component) {
        writer.beginObject();
        writer.key("type").valueString(entry.componentClass().getName());
        writer.key("displayName").valueString(entry.displayName());
        writer.key("fields").beginObject();
        for (ExportedProperty property : EditorReflection.scan(component)) {
            writeProperty(writer, property);
        }
        writer.endObject();
        writer.endObject();
    }

    private void writeProperty(JsonWriter writer, ExportedProperty property) {
        writer.key(property.fieldName());
        Object value = property.read();
        switch (property.kind()) {
            case FLOAT -> writer.valueNumber((float) value);
            case INT -> writer.valueNumber((int) value);
            case BOOLEAN -> writer.valueBoolean((boolean) value);
            case STRING -> writer.valueString((String) value);
            case VECTOR3 -> writeVector3(writer, (Vector3f) value);
            case QUATERNION -> writeQuaternion(writer, (Quaternionf) value);
            default -> writer.valueString("(unsupported)");
        }
    }

    private void writeVector3(JsonWriter writer, Vector3f vector) {
        writer.beginArray();
        writer.valueNumber(vector.x);
        writer.valueNumber(vector.y);
        writer.valueNumber(vector.z);
        writer.endArray();
    }

    private void writeQuaternion(JsonWriter writer, Quaternionf rotation) {
        writer.beginArray();
        writer.valueNumber(rotation.x);
        writer.valueNumber(rotation.y);
        writer.valueNumber(rotation.z);
        writer.valueNumber(rotation.w);
        writer.endArray();
    }

    @SuppressWarnings("unchecked")
    public void load(Scene scene, Path path) throws IOException {
        String text = Files.readString(path);
        Map<String, Object> root = new JsonReader(text).readRootObject();
        clearScene(scene);
        List<Object> gameObjectsJson = (List<Object>) root.getOrDefault("gameObjects", List.of());
        for (Object element : gameObjectsJson) {
            Map<String, Object> gameObjectJson = (Map<String, Object>) element;
            scene.addGameObject(buildGameObject(gameObjectJson));
        }
        scene.advanceTick();
    }

    private void clearScene(Scene scene) {
        List<GameObject> existing = new java.util.ArrayList<>(scene.gameObjects());
        for (GameObject gameObject : existing) {
            scene.removeGameObject(gameObject);
        }
        scene.advanceTick();
    }

    @SuppressWarnings("unchecked")
    private GameObject buildGameObject(Map<String, Object> gameObjectJson) {
        String name = (String) gameObjectJson.getOrDefault("name", "Unnamed");
        GameObject gameObject = new GameObject(name);
        List<Object> componentsJson = (List<Object>) gameObjectJson.getOrDefault("components", List.of());
        for (Object componentObject : componentsJson) {
            Map<String, Object> componentJson = (Map<String, Object>) componentObject;
            attachComponent(gameObject, componentJson);
        }
        return gameObject;
    }

    @SuppressWarnings("unchecked")
    private void attachComponent(GameObject gameObject, Map<String, Object> componentJson) {
        String typeName = (String) componentJson.get("type");
        EditorComponentRegistry.Entry entry = findEntry(typeName);
        if (entry == null) {
            return;
        }
        IComponent component = entry.factory().get();
        Map<String, Object> fields = (Map<String, Object>) componentJson.getOrDefault("fields", Map.of());
        applyFields(component, fields);
        gameObject.addComponent(component);
    }

    private EditorComponentRegistry.Entry findEntry(String typeName) {
        if (typeName == null) {
            return null;
        }
        for (EditorComponentRegistry.Entry entry : componentRegistry.entries()) {
            if (entry.componentClass().getName().equals(typeName)) {
                return entry;
            }
        }
        return null;
    }

    private void applyFields(IComponent component, Map<String, Object> fields) {
        for (ExportedProperty property : EditorReflection.scan(component)) {
            Object value = fields.get(property.fieldName());
            if (value == null) {
                continue;
            }
            applyProperty(property, value);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyProperty(ExportedProperty property, Object value) {
        switch (property.kind()) {
            case FLOAT -> property.writeFloat(asFloat(value));
            case INT -> property.writeInt((int) asFloat(value));
            case BOOLEAN -> property.writeBoolean(value instanceof Boolean booleanValue && booleanValue);
            case STRING -> property.writeObject(value.toString());
            case VECTOR3 -> {
                Vector3f target = (Vector3f) property.read();
                List<Object> values = (List<Object>) value;
                target.set(asFloat(values.get(0)), asFloat(values.get(1)), asFloat(values.get(2)));
            }
            case QUATERNION -> {
                Quaternionf target = (Quaternionf) property.read();
                List<Object> values = (List<Object>) value;
                target.set(asFloat(values.get(0)), asFloat(values.get(1)), asFloat(values.get(2)), asFloat(values.get(3)));
            }
            default -> {
            }
        }
    }

    private static float asFloat(Object number) {
        if (number instanceof Number numericValue) {
            return numericValue.floatValue();
        }
        return 0.0f;
    }
}
