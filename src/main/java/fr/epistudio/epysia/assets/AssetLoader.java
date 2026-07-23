package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;

public interface AssetLoader<T> {

    Class<T> assetType();

    String[] supportedExtensions();

    T load(EngineServices services, String path);

    default void dispose(EngineServices services, T value) {
    }

    default void unloadAll() {
    }
}
