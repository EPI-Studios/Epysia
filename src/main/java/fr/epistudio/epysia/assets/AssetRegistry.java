package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AssetRegistry {

    private final Map<Class<?>, AssetLoader<?>> loadersByType = new HashMap<>();
    private final Map<String, Object> cache = new HashMap<>();
    private final EngineServices services;
    private Optional<AssetDatabase> database = Optional.empty();

    public AssetRegistry(EngineServices services) {
        this.services = services;
    }

    public void setDatabase(AssetDatabase database) {
        this.database = Optional.ofNullable(database);
    }

    public Optional<AssetDatabase> database() {
        return database;
    }

    public <T> void register(AssetLoader<T> loader) {
        loadersByType.put(loader.assetType(), loader);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<AssetLoader<T>> loaderFor(Class<T> type) {
        return Optional.ofNullable((AssetLoader<T>) loadersByType.get(type));
    }

    public List<String> extensionsFor(Class<?> type) {
        AssetLoader<?> loader = loadersByType.get(type);
        if (loader == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String extension : loader.supportedExtensions()) {
            out.add(extension);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> resolve(Class<T> type, String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        String cacheKey = type.getName() + "::" + path;
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            return Optional.of((T) cached);
        }
        Optional<AssetLoader<T>> loader = loaderFor(type);
        if (loader.isEmpty()) {
            return Optional.empty();
        }
        T value = loader.get().load(services, path);
        if (value == null) {
            return Optional.empty();
        }
        cache.put(cacheKey, value);
        return Optional.of(value);
    }

    public void unload(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        cache.keySet().removeIf(key -> key.endsWith("::" + path));
    }

    public void clear() {
        cache.clear();
    }
}
