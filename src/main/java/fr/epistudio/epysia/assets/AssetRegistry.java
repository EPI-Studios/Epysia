package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;

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

    public <T> Optional<T> resolve(Class<T> type, String path) {
        return lookup(type, path, false);
    }

    public <T> Optional<T> acquire(Class<T> type, String path) {
        return lookup(type, path, true);
    }

    public void release(Class<?> type, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        Entry entry = cache.get(cacheKey(type, path));
        if (entry != null && entry.refCount > 0) {
            entry.refCount--;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> lookup(Class<T> type, String path, boolean owning) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        Entry entry = cache.computeIfAbsent(cacheKey(type, path), ignored -> loadEntry(type, path));
        if (entry == null) {
            return Optional.empty();
        }
        if (owning) {
            entry.refCount++;
            entry.counted = true;
        }
        return Optional.of((T) entry.value);
    }

    private <T> Entry loadEntry(Class<T> type, String path) {
        Optional<AssetLoader<T>> loader = loaderFor(type);
        if (loader.isEmpty()) {
            return null;
        }
        T value = loader.get().load(services, path);
        if (value == null) {
            return null;
        }
        return new Entry(value, loader.get());
    }

    @SuppressWarnings("unchecked")
    public <T> T resolveOrCompute(Class<T> type, String path, Supplier<T> producer) {
        Entry entry = cache.get(cacheKey(type, path));
        if (entry != null) {
            return (T) entry.value;
        }
        T produced = producer.get();
        if (produced != null) {
            cache.put(cacheKey(type, path), new Entry(produced, null));
        }
        return produced;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> loadedMatching(Class<T> type, Predicate<String> pathFilter) {
        String prefix = type.getName() + "::";
        List<T> matches = new ArrayList<>();
        for (Map.Entry<String, Entry> entry : cache.entrySet()) {
            if (entry.getKey().startsWith(prefix)
                    && pathFilter.test(entry.getKey().substring(prefix.length()))) {
                matches.add((T) entry.getValue().value);
            }
        }
        return matches;
    }

    public void unloadUnused() {
        Iterator<Entry> entries = cache.values().iterator();
        while (entries.hasNext()) {
            Entry entry = entries.next();
            if (entry.counted && entry.refCount <= 0) {
                dispose(entry);
                entries.remove();
            }
        }
    }

    public void unload(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        String suffix = "::" + path.trim();
        Iterator<Map.Entry<String, Entry>> entries = cache.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Entry> mapEntry = entries.next();
            if (mapEntry.getKey().endsWith(suffix)) {
                dispose(mapEntry.getValue());
                entries.remove();
            }
        }
    }

    public void clear() {
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

    private static String cacheKey(Class<?> type, String path) {
        return type.getName() + "::" + path.trim();
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
