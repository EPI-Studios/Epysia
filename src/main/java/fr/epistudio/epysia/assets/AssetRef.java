package fr.epistudio.epysia.assets;

import java.util.Optional;

public final class AssetRef<T> {

    private final Class<T> type;
    private String path;
    private String guid;
    private transient T cached;
    private transient AssetRegistry acquiredFrom;
    private transient AssetUri acquiredUri = AssetUri.empty();
    private transient AssetUri resolvedUri = AssetUri.empty();
    private transient AssetVariant variant = AssetVariant.none();

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

    public void setUri(AssetUri uri) {
        setPath(uri.toString());
    }

    public void setReference(AssetUri uri, String guid) {
        setPath(uri.toString());
        setGuid(guid);
    }

    public AssetUri resolvedUri() {
        return resolvedUri;
    }

    public AssetVariant variant() {
        return variant;
    }

    public void setVariant(AssetVariant variant) {
        if (!this.variant.equals(variant)) {
            releaseHeld();
        }
        this.variant = variant;
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
        AssetUri effective = effectiveUri(registry);
        resolvedUri = effective;
        Optional<T> loaded = registry.acquire(type, effective, variant);
        loaded.ifPresent(value -> holdAcquired(registry, effective, value));
        return loaded;
    }

    private AssetUri effectiveUri(AssetRegistry registry) {
        AssetUri interpreted = LegacyAssetReferences.interpret(path, registry);
        return databasePath(registry).map(AssetUri::project).orElse(interpreted);
    }

    private Optional<String> databasePath(AssetRegistry registry) {
        if (guid.isEmpty()) {
            return Optional.empty();
        }
        return registry.database().flatMap(database -> database.pathForGuid(guid));
    }

    private void holdAcquired(AssetRegistry registry, AssetUri uri, T value) {
        cached = value;
        acquiredFrom = registry;
        acquiredUri = uri;
    }

    private void releaseHeld() {
        if (acquiredFrom != null) {
            acquiredFrom.release(type, acquiredUri, variant);
            acquiredFrom = null;
            acquiredUri = AssetUri.empty();
        }
        cached = null;
    }

    public Optional<T> direct() {
        return Optional.ofNullable(cached);
    }

    public T directOrNull() {
        return cached;
    }

    public void setDirect(T value) {
        releaseHeld();
        this.cached = value;
    }
}
