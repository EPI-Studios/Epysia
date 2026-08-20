package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.GameObjectJsonCodec;
import fr.epistudio.epysia.scene.serialization.JsonReader;
import org.joml.Quaternionf;

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
        return instantiate(Files.readString(path), scene, services, portableSource(path, services));
    }

    private static String portableSource(Path path, EngineServices services) {
        if (services == null) {
            return path.toString();
        }
        String uri = services.assets().locator().fromFile(path).toString();
        return uri.isEmpty() ? path.toString() : uri;
    }

    public GameObject instantiate(String text, Scene scene, EngineServices services) {
        return instantiate(text, scene, services, "");
    }

    @SuppressWarnings("unchecked")
    public GameObject instantiate(String text, Scene scene, EngineServices services,
                                  String prefabSource) {
        Map<String, Object> root = new JsonReader(text).readRootObject();
        List<Object> gameObjectsJson = (List<Object>) root.getOrDefault("gameObjects", List.of());
        List<GameObject> instantiated = codec.readGameObjectArray(
                gameObjectsJson, GameObjectJsonCodec.IdentityPolicy.FRESH_IDS);
        if (instantiated.isEmpty()) {
            throw new EpysiaException("Prefab contains no game objects");
        }
        linkToPrefab(instantiated, prefabSource);
        for (GameObject gameObject : instantiated) {
            scene.addGameObject(gameObject);
        }
        scene.advanceTick();
        codec.invokeOnLoad(instantiated, services);
        return instantiated.get(0);
    }

    public GameObject instantiateUnder(GameObject parent, Path path, Scene scene, EngineServices services)
            throws IOException {
        return attachTo(parent, instantiate(path, scene, services));
    }

    public GameObject instantiateUnder(GameObject parent, String text, Scene scene, EngineServices services) {
        return attachTo(parent, instantiate(text, scene, services));
    }

    public static GameObject attachTo(GameObject parent, GameObject instantiated) {
        if (!instantiated.setParent(parent)) {
            return instantiated;
        }
        instantiated.getComponent(Transform3D.class).ifPresent(PrefabInstantiator::alignToParent);
        return instantiated;
    }

    private static void alignToParent(Transform3D transform) {
        transform.setPosition(0.0f, 0.0f, 0.0f);
        transform.setRotation(new Quaternionf());
    }

    private static void linkToPrefab(List<GameObject> instantiated, String prefabSource) {
        if (prefabSource.isEmpty()) {
            return;
        }
        for (int index = 0; index < instantiated.size(); index++) {
            instantiated.get(index).linkToPrefab(prefabSource, index).clearOverrides();
        }
    }
}
