package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.concurrent.MainThread;
import fr.epistudio.epysia.logging.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AssetRegistry {
    private final Map<Class<?>, AssetLoader<?>> loadersByType = new HashMap<>();
    private final Map<String, Entry> cache = new HashMap<>();
    private final EngineServices services;
    private Optional<AssetDatabase> database = Optional.empty();
    private AssetLocator locator = AssetLocator.withoutProject();

    public AssetRegistry(EngineServices services) {
        this.services = services;
    }

    public void attachProject(Path projectRoot) {
        AssetResolvers.useProjectRoot(projectRoot);
        locator = AssetLocator.forProject(projectRoot);
        setDatabase(AssetDatabase.open(projectRoot));
    }

    public AssetLocator locator() {
        return locator;
    }

    public Logger logger() {
        return services.logger();
    }

    public void setDatabase(AssetDatabase database) {
        this.database = Optional.ofNullable(database);
    }

    public Optional<AssetDatabase> database() {
        return database;
    }

    public <T> void register(AssetLoader<T> loader) {
        MainThread.require("AssetRegistry.register");
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

    public <T> Optional<T> resolve(Class<T> type, String storedPath) {
        return resolve(type, LegacyAssetReferences.interpret(storedPath, this), AssetVariant.none());
    }

    public <T> Optional<T> acquire(Class<T> type, String storedPath) {
        return acquire(type, LegacyAssetReferences.interpret(storedPath, this), AssetVariant.none());
    }

    public <T> Optional<T> resolve(Class<T> type, AssetUri uri, AssetVariant variant) {
        return lookup(type, uri, variant, false);
    }

    public <T> Optional<T> acquire(Class<T> type, AssetUri uri, AssetVariant variant) {
        return lookup(type, uri, variant, true);
    }

    public void release(Class<?> type, AssetUri uri, AssetVariant variant) {
        MainThread.require("AssetRegistry.release");
        if (uri.isEmpty()) {
            return;
        }
        Entry entry = cache.get(cacheKey(type, uri, variant));
        if (entry != null && entry.refCount > 0) {
            entry.refCount--;
        }
    }

    public void invalidate(AssetUri uri) {
        MainThread.require("AssetRegistry.invalidate");
        String marker = "::" + uri + "|";
        Iterator<Map.Entry<String, Entry>> entries = cache.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Entry> mapEntry = entries.next();
            if (mapEntry.getKey().contains(marker)) {
                dispose(mapEntry.getValue());
                entries.remove();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> lookup(Class<T> type, AssetUri uri, AssetVariant variant, boolean owning) {
        MainThread.require("AssetRegistry.resolve");
        if (uri.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = cached(type, uri, variant);
        if (entry == null) {
            return Optional.empty();
        }
        if (owning) {
            entry.refCount++;
            entry.counted = true;
        }
        return Optional.of((T) entry.value);
    }

    private <T> Entry cached(Class<T> type, AssetUri uri, AssetVariant variant) {
        String key = cacheKey(type, uri, variant);
        Entry entry = cache.get(key);
        if (entry != null) {
            return entry;
        }
        Entry loaded = loadEntry(type, new AssetLoadRequest(uri, variant));
        if (loaded == null) {
            return null;
        }
        Entry raced = cache.putIfAbsent(key, loaded);
        if (raced == null) {
            return loaded;
        }
        dispose(loaded);
        return raced;
    }

    private <T> Entry loadEntry(Class<T> type, AssetLoadRequest request) {
        Optional<AssetLoader<T>> loader = loaderFor(type);
        if (loader.isEmpty()) {
            return null;
        }
        T value = loader.get().load(services, request);
        if (value == null) {
            return null;
        }
        return new Entry(value, loader.get());
    }

    @SuppressWarnings("unchecked")
    public <T> T resolveOrCompute(Class<T> type, String key, Supplier<T> producer) {
        MainThread.require("AssetRegistry.resolveOrCompute");
        String computedKey = type.getName() + "::" + key;
        Entry entry = cache.get(computedKey);
        if (entry != null) {
            return (T) entry.value;
        }
        T produced = producer.get();
        if (produced != null) {
            cache.put(computedKey, new Entry(produced, null));
        }
        return produced;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> loaded(Class<T> type, AssetUri uri) {
        String marker = type.getName() + "::" + uri + "|";
        List<T> matches = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : cache.entrySet()) {
            if (entry.getKey().startsWith(marker)) {
                matches.add((T) entry.getValue().value);
            }
        }
        return matches;
    }

    public void unloadUnused() {
        MainThread.require("AssetRegistry.unloadUnused");
        Iterator<Entry> entries = cache.values().iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            if (entry.counted && entry.refCount <= 0) {
                dispose(entry);
                entries.remove();
            }
        }
    }

    public void clear() {
        MainThread.require("AssetRegistry.clear");
        for (Entry entry : cache.values()) {
            dispose(entry);
        }
        cache.clear();
        for (AssetLoader<?> loader : loadersByType.values()) {
            loader.unloadAll();
        }
    }

    private void dispose(Entry entry) {
        if (entry.loader != null) {
            disposeTyped(entry.loader, entry.value);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void disposeTyped(AssetLoader<T> loader, Object value) {
        loader.dispose(services, (T) value);
    }

    private static String cacheKey(Class<?> type, AssetUri uri, AssetVariant variant) {
        return type.getName() + "::" + uri + "|" + variant.fingerprint();
    }

    private static final class Entry {
        private final Object value;
        private final AssetLoader<?> loader;
        private int refCount;
        private boolean counted;

        private Entry(Object value, AssetLoader<?> loader) {
            this.value = value;
            this.loader = loader;
        }
    }
}
