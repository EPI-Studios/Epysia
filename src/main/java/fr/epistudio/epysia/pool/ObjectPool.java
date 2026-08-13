package fr.epistudio.epysia.pool;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabInstantiator;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Behaviour;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class ObjectPool {

    private final String prefabPath;
    private final String prefabText;
    private final PrefabInstantiator instantiator;
    private final EngineServices services;
    private final Deque<GameObject> idle = new ArrayDeque<>();
    private final Set<GameObject> live = Collections.newSetFromMap(new IdentityHashMap<>());
    private int createdCount;

    ObjectPool(String prefabPath, String prefabText, PrefabInstantiator instantiator,
               EngineServices services) {
        this.prefabPath = prefabPath;
        this.prefabText = prefabText;
        this.instantiator = instantiator;
        this.services = services;
    }

    public String prefabPath() {
        return prefabPath;
    }

    public int idleCount() {
        dropDeadIdle();
        return idle.size();
    }

    public int liveCount() {
        return live.size();
    }

    public int createdCount() {
        return createdCount;
    }

    public void prewarm(int count) {
        for (int index = idle.size(); index < count; index++) {
            GameObject created = create();
            deactivate(created);
            idle.add(created);
        }
    }

    public GameObject spawn() {
        GameObject spawned = takeIdle();
        activate(spawned);
        live.add(spawned);
        dispatchSpawn(spawned);
        return spawned;
    }

    public GameObject spawn(Vector3fc position) {
        GameObject spawned = takeIdle();
        placeAt(spawned, position);
        activate(spawned);
        live.add(spawned);
        dispatchSpawn(spawned);
        return spawned;
    }

    public GameObject spawn(Vector3fc position, Quaternionf rotation) {
        GameObject spawned = takeIdle();
        placeAt(spawned, position);
        Transform3D transform = spawned.transform3DOrNull();
        if (transform != null) {
            transform.setRotation(rotation);
        }
        activate(spawned);
        live.add(spawned);
        dispatchSpawn(spawned);
        return spawned;
    }

    public boolean despawn(GameObject gameObject) {
        if (gameObject == null || !live.remove(gameObject)) {
            return false;
        }
        if (!gameObject.isAlive()) {
            return true;
        }
        dispatchDespawn(gameObject);
        deactivate(gameObject);
        idle.add(gameObject);
        return true;
    }

    public boolean owns(GameObject gameObject) {
        return live.contains(gameObject) || idle.contains(gameObject);
    }

    public void despawnAll() {
        for (GameObject gameObject : List.copyOf(live)) {
            despawn(gameObject);
        }
    }

    private GameObject takeIdle() {
        dropDeadIdle();
        GameObject reused = idle.poll();
        return reused != null ? reused : create();
    }

    private void dropDeadIdle() {
        idle.removeIf(gameObject -> !gameObject.isAlive());
    }

    private GameObject create() {
        Scene scene = services.scene();
        GameObject created = instantiator.instantiate(prefabText, scene, services, prefabPath);
        scene.advanceTick();
        createdCount++;
        return created;
    }

    private static void placeAt(GameObject gameObject, Vector3fc position) {
        Transform3D transform = gameObject.transform3DOrNull();
        if (transform != null) {
            transform.setPosition(position.x(), position.y(), position.z());
        }
    }

    private static void activate(GameObject gameObject) {
        gameObject.setActive(true);
    }

    private static void deactivate(GameObject gameObject) {
        gameObject.setActive(false);
    }

    private void dispatchSpawn(GameObject gameObject) {
        for (IComponent component : gameObject.components()) {
            if (component instanceof Behaviour behaviour) {
                guard(() -> behaviour.onSpawn(), "onSpawn", behaviour);
            }
        }
    }

    private void dispatchDespawn(GameObject gameObject) {
        for (IComponent component : gameObject.components()) {
            if (component instanceof Behaviour behaviour) {
                guard(() -> behaviour.onDespawn(), "onDespawn", behaviour);
            }
        }
    }

    private void guard(Runnable hook, String name, Behaviour behaviour) {
        try {
            hook.run();
        } catch (RuntimeException error) {
            services.logger().error("[ObjectPool] " + name + " threw in "
                    + behaviour.getClass().getName(), error);
        }
    }

    static String readPrefab(EngineServices services, String prefabPath) {
        return PrefabText.read(services, prefabPath)
                .orElseThrow(() -> new EpysiaException("Prefab not found for pooling: " + prefabPath));
    }

    List<GameObject> liveObjects() {
        return new ArrayList<>(live);
    }
}
