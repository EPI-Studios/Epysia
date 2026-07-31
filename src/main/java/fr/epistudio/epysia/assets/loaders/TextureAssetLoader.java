package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.texture.Texture2D;

public final class TextureAssetLoader implements AssetLoader<TextureHandle> {

    @Override
    public Class<TextureHandle> assetType() {
        return TextureHandle.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{".png", ".jpg", ".jpeg", ".tga", ".bmp", ".hdr", ".exr"};
    }

    @Override
    public TextureHandle load(EngineServices services, AssetLoadRequest request) {
        AssetLocator locator = services.assets().locator();
        try {
            TextureImportSettings settings =
                    TextureImportSettings.from(request.settings(locator), request.variant());
            return Texture2D.load(services.renderBackend(), locator.resolvedPath(request.uri()),
                    settings.format(), settings.wrap(), settings.filter(), Texture2D.samplingOf(settings));
        } catch (RuntimeException error) {
            services.logger().error("[TextureAssetLoader] Failed to load " + request.uri(), error);
            return null;
        }
    }

    @Override
    public void dispose(EngineServices services, TextureHandle value) {
        services.renderBackend().destroy(value);
    }
}
