package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.JsonReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class PrefabRefresher {

    private final Function<String, Optional<String>> prefabTextSource;
    private final PrefabFieldApplier applier;
    private final Map<String, List<Object>> parsedPrefabs = new HashMap<>();

    public PrefabRefresher(Function<String, Optional<String>> prefabTextSource,
                           PrefabFieldApplier applier) {
        this.prefabTextSource = prefabTextSource;
        this.applier = applier;
    }

    public int refresh(Scene scene) {
        parsedPrefabs.clear();
        int refreshed = 0;
        for (GameObject gameObject : scene.gameObjects()) {
            if (gameObject.isPrefabInstance() && refreshOne(gameObject)) {
                refreshed++;
            }
        }
        return refreshed;
    }

    public boolean refreshOne(GameObject gameObject) {
        return prefabObject(gameObject).map(entry -> applyEntry(gameObject, entry)).orElse(false);
    }

    public void revertEverything(GameObject gameObject) {
        gameObject.clearOverrides();
        refreshOne(gameObject);
    }

    public void revertProperty(GameObject gameObject, Class<?> componentClass, String fieldName) {
        if (!gameObject.isOverridden(componentClass, fieldName)) {
            return;
        }
        List<String> kept = new ArrayList<>(gameObject.overriddenProperties());
        kept.remove(GameObject.overrideKey(componentClass, fieldName));
        gameObject.clearOverrides();
        for (String key : kept) {
            gameObject.markOverridden(key);
        }
        refreshOne(gameObject);
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> prefabObject(GameObject gameObject) {
        List<Object> objects = objectsOf(gameObject.prefabSource());
        int objectId = gameObject.prefabObjectId();
        if (objectId < 0 || objectId >= objects.size()) {
            return Optional.empty();
        }
        return objects.get(objectId) instanceof Map<?, ?> entry
                ? Optional.of((Map<String, Object>) entry)
                : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<Object> objectsOf(String prefabSource) {
        return parsedPrefabs.computeIfAbsent(prefabSource, source -> prefabTextSource.apply(source)
                .map(text -> (List<Object>) new JsonReader(text).readRootObject()
                        .getOrDefault("gameObjects", List.of()))
                .orElse(List.of()));
    }

    @SuppressWarnings("unchecked")
    private boolean applyEntry(GameObject gameObject, Map<String, Object> entry) {
        List<Object> components = (List<Object>) entry.getOrDefault("components", List.of());
        boolean changed = false;
        for (Object componentObject : components) {
            if (componentObject instanceof Map<?, ?> componentJson) {
                changed |= applyComponent(gameObject, (Map<String, Object>) componentJson);
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private boolean applyComponent(GameObject gameObject, Map<String, Object> componentJson) {
        if (!(componentJson.get("type") instanceof String typeName)) {
            return false;
        }
        IComponent target = matchingComponent(gameObject, typeName);
        if (target == null) {
            return false;
        }
        Map<String, Object> fields =
                (Map<String, Object>) componentJson.getOrDefault("fields", Map.of());
        Map<String, Object> inherited = withoutOverrides(gameObject, target, fields);
        if (inherited.isEmpty()) {
            return false;
        }
        applier.applyFields(target, inherited);
        return true;
    }

    private static Map<String, Object> withoutOverrides(GameObject gameObject, IComponent target,
                                                        Map<String, Object> fields) {
        Map<String, Object> inherited = new LinkedHashMap<>();
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            if (!gameObject.isOverridden(target.getClass(), field.getKey())) {
                inherited.put(field.getKey(), field.getValue());
            }
        }
        return inherited;
    }

    private static IComponent matchingComponent(GameObject gameObject, String typeName) {
        for (IComponent component : gameObject.components()) {
            if (component.getClass().getName().equals(typeName)) {
                return component;
            }
        }
        return null;
    }
}
