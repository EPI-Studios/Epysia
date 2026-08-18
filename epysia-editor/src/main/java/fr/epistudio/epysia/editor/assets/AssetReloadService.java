package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetRefFields;
import fr.epistudio.epysia.assets.AssetUri;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.components.MeshRenderSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class AssetReloadService {

    private final AssetChangeWatcher watcher;
    private final Supplier<EngineServices> services;
    private final Supplier<List<SceneDocument>> documents;

    public AssetReloadService(Path projectRoot, Supplier<EngineServices> services,
                              Supplier<List<SceneDocument>> documents) {
        this.watcher = new AssetChangeWatcher(projectRoot);
        this.services = services;
        this.documents = documents;
    }

    public List<Path> poll(float deltaTimeSeconds) {
        List<Path> changed = watcher.poll(deltaTimeSeconds);
        List<Path> reloaded = new ArrayList<>();
        for (Path file : changed) {
            if (reload(file)) {
                reloaded.add(file);
            }
        }
        return reloaded;
    }

    private boolean reload(Path file) {
        EngineServices engineServices = services.get();
        AssetUri uri = engineServices.assets().locator().fromFile(file);
        if (uri.isEmpty()) {
            return false;
        }
        engineServices.assets().invalidate(uri);
        boolean refreshed = false;
        for (SceneDocument document : documents.get()) {
            for (GameObject gameObject : document.scene().gameObjects()) {
                refreshed |= refreshComponents(gameObject, uri.toString(), engineServices);
            }
        }
        return refreshed;
    }

    private boolean refreshComponents(GameObject gameObject, String storedPath, EngineServices engineServices) {
        boolean refreshed = false;
        for (IComponent component : new ArrayList<>(gameObject.components())) {
            if (!referencesPath(component, storedPath)) {
                continue;
            }
            component.onLoad(engineServices);
            refreshed = true;
        }
        return refreshed;
    }

    private static boolean referencesPath(IComponent component, String storedPath) {
        if (AssetRefFields.releaseMatching(component, storedPath)) {
            return true;
        }
        return component instanceof MeshRenderSource source && usesTexture(source, storedPath);
    }

    private static boolean usesTexture(MeshRenderSource source, String storedPath) {
        for (int slot = 0; slot < MAXIMUM_MATERIAL_SLOTS; slot++) {
            Material material = source.materialForSlot(slot).orElse(null);
            if (material == null) {
                continue;
            }
            if (material.texturePaths().containsValue(storedPath)) {
                return true;
            }
        }
        return false;
    }

    private static final int MAXIMUM_MATERIAL_SLOTS = 16;
}
