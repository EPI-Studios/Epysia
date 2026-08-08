package fr.epistudio.epysia.net;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetDatabase;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.replication.NetworkObject;
import fr.epistudio.epysia.prefab.PrefabInstantiator;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.Scene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class PrefabSpawner {
    private final Logger logger;
    private PrefabInstantiator instantiator;

    public PrefabSpawner(Logger logger) {
        this.logger = logger;
    }

    public Optional<GameObject> instantiate(EngineServices services, Scene scene, SpawnRecord record) {
        Optional<Path> prefabPath = resolvePath(services, record.prefabGuid());
        if (prefabPath.isEmpty()) {
            logger.warn("[net] cannot spawn network object " + record.networkId()
                    + ": no prefab resolves to " + record.prefabGuid());
            return Optional.empty();
        }
        return readPrefab(services, scene, prefabPath.get())
                .map(gameObject -> configure(gameObject, record));
    }

    private Optional<GameObject> readPrefab(EngineServices services, Scene scene, Path path) {
        try {
            return Optional.of(instantiatorFor().instantiate(Files.readString(path), scene, services));
        } catch (IOException | RuntimeException failure) {
            logger.error("[net] failed to instantiate prefab " + path, failure);
            return Optional.empty();
        }
    }

    private PrefabInstantiator instantiatorFor() {
        if (instantiator == null) {
            ComponentRegistry registry = new ComponentRegistry();
            registry.populateFromScan(ComponentScanner.scan());
            instantiator = new PrefabInstantiator(registry);
        }
        return instantiator;
    }

    private static GameObject configure(GameObject gameObject, SpawnRecord record) {
        Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
        if (transform != null) {
            record.applyTo(transform);
        }
        NetworkObject networkObject = gameObject.getComponentOrNull(NetworkObject.class);
        if (networkObject == null) {
            networkObject = gameObject.addComponent(new NetworkObject());
        }
        networkObject.setPrefabGuid(record.prefabGuid())
                .assignNetworkId(record.networkId())
                .assignOwner(record.ownerPeer())
                .markSpawnedAtRuntime();
        return gameObject;
    }

    private static Optional<Path> resolvePath(EngineServices services, String prefabGuid) {
        Optional<Path> projectRoot = services.assets().locator().projectRoot();
        if (projectRoot.isEmpty()) {
            return Optional.empty();
        }
        Optional<AssetDatabase> database = services.assets().database();
        Optional<String> relative = database.flatMap(index -> index.pathForGuid(prefabGuid));
        return relative.or(() -> Optional.of(prefabGuid))
                .map(projectRoot.get()::resolve)
                .filter(Files::isRegularFile);
    }
}
