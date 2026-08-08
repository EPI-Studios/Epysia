package fr.epistudio.epysia.scene.serialization;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class SceneSerializer {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMembers(Map<?, ?> members) {
        return (Map<String, Object>) members;
    }

    private final GameObjectJsonCodec codec;
    private final PostEffectStackJsonCodec postEffectCodec = new PostEffectStackJsonCodec();
    private final PostProcessSettingsJsonCodec postProcessCodec = new PostProcessSettingsJsonCodec();

    public SceneSerializer(ComponentRegistry componentRegistry) {
        this.codec = new GameObjectJsonCodec(componentRegistry);
    }

    public void save(Scene scene, Path path) throws IOException {
        save(scene, path, GameObject::persistent);
    }

    public void save(Scene scene, Path path, Predicate<GameObject> include) throws IOException {
        Files.writeString(path, serialize(scene, include));
    }

    public String serialize(Scene scene, Predicate<GameObject> include) {
        List<GameObject> exported = new ArrayList<>();
        for (GameObject gameObject : scene.gameObjects()) {
            if (gameObject.persistent() && include.test(gameObject)) {
                exported.add(gameObject);
            }
        }
        JsonWriter writer = new JsonWriter();
        writer.beginObject();
        writer.key("name").valueString(scene.name());
        writer.key("gameObjects");
        codec.writeGameObjectArray(writer, exported);
        writer.key("postProcess");
        postProcessCodec.write(writer, scene.postProcess());
        if (!scene.postEffects().isEmpty()) {
            writer.key("postEffects");
            postEffectCodec.writeStack(writer, scene.postEffects());
        }
        writer.endObject();
        return writer.toString();
    }

    public void load(Scene scene, Path path) throws IOException {
        load(scene, path, null);
    }

    public void load(Scene scene, Path path, EngineServices services) throws IOException {
        deserialize(scene, Files.readString(path), services);
    }

    @SuppressWarnings("unchecked")
    public void deserialize(Scene scene, String text, EngineServices services) {
        Map<String, Object> root = new JsonReader(text).readRootObject();
        clearScene(scene);
        if (root.get("postProcess") instanceof Map<?, ?> postProcessJson) {
            postProcessCodec.read(castMembers(postProcessJson), scene.postProcess());
        }
        scene.postEffects().clear();
        if (root.get("postEffects") instanceof List<?> postEffectsJson) {
            postEffectCodec.readStack(postEffectsJson, scene.postEffects());
        }
        List<Object> gameObjectsJson = (List<Object>) root.getOrDefault("gameObjects", List.of());
        List<GameObject> loaded = codec.readGameObjectArray(
                gameObjectsJson, GameObjectJsonCodec.IdentityPolicy.PRESERVE_IDS);
        for (GameObject gameObject : loaded) {
            scene.addGameObject(gameObject);
        }
        scene.advanceTick();
        if (services != null) {
            codec.invokeOnLoad(loaded, services);
        }
    }

    public void applyFields(IComponent component, Map<String, Object> fields) {
        codec.applyFieldsWithoutReferences(component, fields);
    }

    private void clearScene(Scene scene) {
        List<GameObject> existing = new ArrayList<>(scene.gameObjects());
        for (GameObject gameObject : existing) {
            scene.removeGameObject(gameObject);
        }
        scene.advanceTick();
    }
}
