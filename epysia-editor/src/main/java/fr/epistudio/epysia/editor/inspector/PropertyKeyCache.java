package fr.epistudio.epysia.editor.inspector;

import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class PropertyKeyCache {

    private static final String SEPARATOR = "#";
    private static final String FIELD_SEPARATOR = ".";
    private static final int MAXIMUM_TRACKED_COMPONENTS = 512;

    private final Map<IComponent, String> prefixes = new IdentityHashMap<>();
    private final Map<String, Map<String, String>> fieldKeys = new HashMap<>();

    public String prefixFor(GameObject gameObject, IComponent component) {
        String cached = prefixes.get(component);
        if (cached != null) {
            return cached;
        }
        evictWhenFull();
        String built = gameObject.id() + SEPARATOR + component.getClass().getName()
                + SEPARATOR + System.identityHashCode(component);
        prefixes.put(component, built);
        return built;
    }

    public String keyFor(String prefix, String fieldName) {
        Map<String, String> byField = fieldKeys.get(prefix);
        if (byField == null) {
            byField = new HashMap<>();
            fieldKeys.put(prefix, byField);
        }
        String cached = byField.get(fieldName);
        if (cached != null) {
            return cached;
        }
        String built = prefix + FIELD_SEPARATOR + fieldName;
        byField.put(fieldName, built);
        return built;
    }

    private void evictWhenFull() {
        if (prefixes.size() < MAXIMUM_TRACKED_COMPONENTS) {
            return;
        }
        prefixes.clear();
        fieldKeys.clear();
    }
}
