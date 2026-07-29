package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.epyinstances.EpyInstancesFormat;
import fr.epistudio.epysia.assets.epyinstances.EpyInstancesSource;
import fr.epistudio.epysia.assets.epyinstances.InstanceTransforms;
import fr.epistudio.epysia.exceptions.EpysiaException;

public final class InstancesAssetLoader implements AssetLoader<InstanceTransforms> {

    @Override
    public Class<InstanceTransforms> assetType() {
        return InstanceTransforms.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EpyInstancesFormat.EXTENSION};
    }

    @Override
    public InstanceTransforms load(EngineServices services, AssetLoadRequest request) {
        String path = services.assets().locator().resolvedPath(request.uri());
        if (!path.endsWith(EpyInstancesFormat.EXTENSION)) {
            throw new EpysiaException("Unsupported instances asset extension: " + path);
        }
        return EpyInstancesSource.load(path);
    }
}
