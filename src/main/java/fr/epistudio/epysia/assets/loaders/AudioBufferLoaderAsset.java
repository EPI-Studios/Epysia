package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.audio.AudioBuffer;
import fr.epistudio.epysia.audio.AudioBufferLoader;

public final class AudioBufferLoaderAsset implements AssetLoader<AudioBuffer> {

    @Override
    public Class<AudioBuffer> assetType() {
        return AudioBuffer.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{".ogg", ".wav", ".mp3"};
    }

    @Override
    public AudioBuffer load(EngineServices services, AssetLoadRequest request) {
        return AudioBufferLoader.loadFromFile(services.assets().locator().resolvedPath(request.uri()));
    }

    @Override
    public void dispose(EngineServices services, AudioBuffer value) {
        value.destroy();
    }
}
