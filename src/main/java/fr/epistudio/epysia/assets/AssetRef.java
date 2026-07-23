package fr.epistudio.epysia.assets;

import java.util.Optional;

public final class AssetRef<T> {

    private final Class<T> type;
    private String path;
    private String guid;
    private transient T cached;
    private transient AssetRegistry acquiredFrom;
    private transient String acquiredPath;

    public AssetRef(Class<T> type) {
        this(type, "");
    }

    public AssetRef(Class<T> type, String path) {
        this.type = type;
        this.path = path == null ? "" : path;
        this.guid = "";
    }

    public Class<T> type() {
        return type;
    }

    public String path() {
        return path;
    }

    public String guid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid == null ? "" : guid;
    }

    public boolean isEmpty() {
        return path.isEmpty() && guid.isEmpty();
    }

    public void setPath(String path) {
        String next = path == null ? "" : path;
        if (!next.equals(this.path)) {
            releaseHeld();
        }
        this.path = next;
    }

    public void clearCache() {
        releaseHeld();
    }

    public void release() {
        releaseHeld();
    }

    public Optional<T> resolve(AssetRegistry registry) {
        if (cached != null) {
            return Optional.of(cached);
        }
        if (isEmpty() || registry == null) {
            return Optional.empty();
        }
        resolvePathFromGuid(registry);
        if (path.isEmpty()) {
            return Optional.empty();
        }
        Optional<T> loaded = registry.acquire(type, path);
        loaded.ifPresent(value -> holdAcquired(registry, value));
        return loaded;
    }

    private void holdAcquired(AssetRegistry registry, T value) {
        cached = value;
        acquiredFrom = registry;
        acquiredPath = path;
    }

    private void releaseHeld() {
        if (acquiredFrom != null) {
            acquiredFrom.release(type, acquiredPath);
            acquiredFrom = null;
            acquiredPath = null;
        }
        cached = null;
    }

    private void resolvePathFromGuid(AssetRegistry registry) {
        if (guid.isEmpty()) {
            return;
        }
        registry.database()
                .flatMap(database -> database.pathForGuid(guid))
                .ifPresent(this::setPath);
    }

    public Optional<T> direct() {
        return Optional.ofNullable(cached);
    }

    public void setDirect(T value) {
        releaseHeld();
        this.cached = value;
    }
}
