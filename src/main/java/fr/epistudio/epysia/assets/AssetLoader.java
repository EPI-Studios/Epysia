package fr.epistudio.epysia.assets;

import fr.epistudio.epysia.EngineServices;

import java.util.Optional;

public interface AssetLoader<T> {

    Class<T> assetType();

    String[] supportedExtensions();

    T load(EngineServices services, AssetLoadRequest request);

    default Optional<Object> readOffThread(AssetLocator locator, AssetLoadRequest request) {
        return Optional.empty();
    }

    default T loadFromRead(EngineServices services, AssetLoadRequest request, Object read) {
        return load(services, request);
    }

    default void dispose(EngineServices services, T value) {
    }

    default void unloadAll() {
    }
}
