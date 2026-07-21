package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.texture.Texture2D;

public final class TextureAssetLoader implements AssetLoader<TextureHandle> {

    public static final String SRGB_PREFIX = "srgb:";

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
            return loadTexture(services.renderBackend(), path);
        } catch (RuntimeException error) {
            services.logger().error("[TextureAssetLoader] Failed to load " + path, error);
            return null;
        }
    }

    private static TextureHandle loadTexture(RenderBackend backend, String path) {
        if (path.startsWith(SRGB_PREFIX)) {
            return Texture2D.load(backend, path.substring(SRGB_PREFIX.length()), TextureFormat.SRGB8_ALPHA8);
        }
        return Texture2D.load(backend, path);
    }
}
