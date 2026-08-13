package fr.epistudio.epysia.scene;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.prefab.PrefabRefresher;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneLoader {

    private final EngineServices services;
    private final SceneSerializer serializer;
    private final Deque<SceneLoadRequest> pending = new ArrayDeque<>();
    private final Map<String, String> preloaded = new ConcurrentHashMap<>();
    private final Set<String> loadedSources = new LinkedHashSet<>();
    private PrefabRefresher prefabRefresher;

    public SceneLoader(EngineServices services, SceneSerializer serializer) {
        this.services = services;
        this.serializer = serializer;
    }

    public void load(String scenePath) {
        pending.add(new SceneLoadRequest(scenePath, SceneLoadMode.REPLACE));
    }

    public void loadAdditive(String scenePath) {
        pending.add(new SceneLoadRequest(scenePath, SceneLoadMode.ADDITIVE));
    }

    public void unload(String scenePath) {
        pending.add(new SceneLoadRequest(scenePath, null));
    }

    public boolean isPreloaded(String scenePath) {
        return preloaded.containsKey(scenePath);
    }

    public Set<String> loadedSources() {
        return Set.copyOf(loadedSources);
    }

    public boolean hasPendingWork() {
        return !pending.isEmpty();
    }

    public void preload(String scenePath) {
        if (preloaded.containsKey(scenePath)) {
            return;
        }
        services.backgroundTasks().submit(() -> readSceneText(scenePath),
                text -> text.ifPresent(loaded -> preloaded.put(scenePath, loaded)),
                failure -> services.logger().error("[SceneLoader] Preload failed for "
                        + scenePath, failure));
    }

    public void discardPreloaded(String scenePath) {
        preloaded.remove(scenePath);
    }

    public void applyPending() {
        while (!pending.isEmpty()) {
            apply(pending.poll());
        }
    }

    private void apply(SceneLoadRequest request) {
        if (request.mode() == null) {
            serializer.unloadSource(services.scene(), request.scenePath());
            loadedSources.remove(request.scenePath());
            services.assets().unloadUnused();
            return;
        }
        readSceneText(request.scenePath())
                .ifPresentOrElse(text -> applyText(request, text),
                        () -> services.logger().error("[SceneLoader] Scene not found: "
                                + request.scenePath()));
    }

    private void applyText(SceneLoadRequest request, String text) {
        if (request.mode() == SceneLoadMode.REPLACE) {
            loadedSources.clear();
        }
        serializer.deserializeInto(services.scene(), text, services,
                request.mode(), request.scenePath());
        if (request.mode() == SceneLoadMode.REPLACE) {
            services.assets().unloadUnused();
        }
        refreshPrefabInstances();
        loadedSources.add(request.scenePath());
        preloaded.remove(request.scenePath());
        services.logger().info("[SceneLoader] " + request.mode() + " " + request.scenePath()
                + " (" + services.scene().gameObjects().size() + " objects live)");
    }

    public int refreshPrefabInstances() {
        int refreshed = prefabRefresher().refresh(services.scene());
        if (refreshed > 0) {
            services.logger().info("[SceneLoader] Refreshed " + refreshed
                    + " prefab instances from their source");
        }
        return refreshed;
    }

    private PrefabRefresher prefabRefresher() {
        if (prefabRefresher == null) {
            prefabRefresher = new PrefabRefresher(this::readPrefabText, serializer::applyFields);
        }
        return prefabRefresher;
    }

    private Optional<String> readPrefabText(String prefabPath) {
        return resolve(prefabPath).flatMap(SceneLoader::readFile);
    }

    private Optional<String> readSceneText(String scenePath) {
        String cached = preloaded.get(scenePath);
        if (cached != null) {
            return Optional.of(cached);
        }
        return resolve(scenePath).flatMap(SceneLoader::readFile);
    }

    private Optional<Path> resolve(String scenePath) {
        Path direct = Path.of(scenePath);
        if (Files.isRegularFile(direct)) {
            return Optional.of(direct);
        }
        return AssetUri.parse(scenePath).flatMap(uri -> services.assets().locator().file(uri));
    }

    private static Optional<String> readFile(Path path) {
        try {
            return Optional.of(Files.readString(path));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    public GameObject keepAcrossSceneChange(GameObject gameObject) {
        return gameObject.setKeepOnSceneChange(true);
    }
}
