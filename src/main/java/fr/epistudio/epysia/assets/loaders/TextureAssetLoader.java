package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.texture.Texture2D;

public final class TextureAssetLoader implements AssetLoader<TextureHandle> {

    @Override
    public Class<TextureHandle> assetType() {
        return TextureHandle.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{".png", ".jpg", ".jpeg"};
    }

    @Override
    public TextureHandle load(EngineServices services, String path) {
        try {
            return Texture2D.load(services.renderBackend(), path);
        } catch (RuntimeException error) {
            services.logger().error("[TextureAssetLoader] Failed to load " + path, error);
            return null;
        }
    }
}
