package fr.epistudio.epysia.prefab;

import fr.epistudio.epysia.components.IComponent;

import java.util.Map;

@FunctionalInterface
public interface PrefabFieldApplier {
    void applyFields(IComponent component, Map<String, Object> fields);
}
