package fr.epistudio.epysia.pool;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabInstantiator;
import fr.epistudio.epysia.reflection.ComponentRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ObjectPools {

    private final EngineServices services;
    private final Map<String, ObjectPool> poolsByPrefab = new LinkedHashMap<>();
    private PrefabInstantiator instantiator;

    public ObjectPools(EngineServices services) {
        this.services = services;
    }

    public ObjectPool forPrefab(String prefabPath) {
        return poolsByPrefab.computeIfAbsent(prefabPath, path ->
                new ObjectPool(path, ObjectPool.readPrefab(services, path), instantiator(), services));
    }

    public Optional<ObjectPool> poolOf(GameObject gameObject) {
        for (ObjectPool pool : poolsByPrefab.values()) {
            if (pool.owns(gameObject)) {
                return Optional.of(pool);
            }
        }
        return Optional.empty();
    }

    public boolean despawn(GameObject gameObject) {
        return poolOf(gameObject).map(pool -> pool.despawn(gameObject)).orElse(false);
    }

    public void despawnAll() {
        for (ObjectPool pool : poolsByPrefab.values()) {
            pool.despawnAll();
        }
    }

    public void clear() {
        poolsByPrefab.clear();
    }

    public int poolCount() {
        return poolsByPrefab.size();
    }

    private PrefabInstantiator instantiator() {
        if (instantiator == null) {
            instantiator = new PrefabInstantiator(ComponentRegistry.populated());
        }
        return instantiator;
    }
}
