package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.GameObjectJsonCodec;
import fr.epistudio.epysia.scene.serialization.JsonReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class PrefabInstantiator {

    private final GameObjectJsonCodec codec;

    public PrefabInstantiator(ComponentRegistry componentRegistry) {
        this.codec = new GameObjectJsonCodec(componentRegistry);
    }

    public GameObject instantiate(Path path, Scene scene, EngineServices services) throws IOException {
        return instantiate(Files.readString(path), scene, services);
    }

    @SuppressWarnings("unchecked")
    public GameObject instantiate(String text, Scene scene, EngineServices services) {
        Map<String, Object> root = new JsonReader(text).readRootObject();
        List<Object> gameObjectsJson = (List<Object>) root.getOrDefault("gameObjects", List.of());
        List<GameObject> instantiated = codec.readGameObjectArray(
                gameObjectsJson, GameObjectJsonCodec.IdentityPolicy.FRESH_IDS);
        if (instantiated.isEmpty()) {
            throw new EpysiaException("Prefab contains no game objects");
        }
        for (GameObject gameObject : instantiated) {
            scene.addGameObject(gameObject);
        }
        scene.advanceTick();
        codec.invokeOnLoad(instantiated, services);
        return instantiated.get(0);
    }
}
