package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import fr.epistudio.epysia.assets.epyprobes.EpyProbesFormat;
import fr.epistudio.epysia.assets.epyprobes.EpyProbesSource;
import fr.epistudio.epysia.exceptions.EpysiaException;

public final class ProbesAssetLoader implements AssetLoader<BakedProbes> {

    @Override
    public Class<BakedProbes> assetType() {
        return BakedProbes.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EpyProbesFormat.EXTENSION};
    }

    @Override
    public BakedProbes load(EngineServices services, String path) {
        if (!path.endsWith(EpyProbesFormat.EXTENSION)) {
            throw new EpysiaException("Unsupported probes asset extension: " + path);
        }
        return EpyProbesSource.load(path);
    }
}
