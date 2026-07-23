package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.texture.Texture2D;

public final class TextureAssetLoader implements AssetLoader<TextureHandle> {

    public static final String SRGB_PREFIX = TexturePathPrefixes.SRGB_PREFIX;
    public static final String CLAMP_PREFIX = TexturePathPrefixes.CLAMP_PREFIX;
    public static final String MIRROR_PREFIX = TexturePathPrefixes.MIRROR_PREFIX;

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

    @Override
    public void dispose(EngineServices services, TextureHandle value) {
        services.renderBackend().destroy(value);
    }

    private static TextureHandle loadTexture(RenderBackend backend, String path) {
        TexturePathPrefixes.ParsedPath parsed = TexturePathPrefixes.parse(path);
        return Texture2D.load(backend, parsed.remainder(), parsed.format(), parsed.wrap());
    }
}
