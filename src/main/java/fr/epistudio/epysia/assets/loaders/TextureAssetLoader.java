package fr.epistudio.epysia.assets.loaders;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.render.texture.HighDynamicRangeImage;
import fr.epistudio.epysia.render.texture.Texture2D;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

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
    public Optional<Object> readOffThread(AssetLocator locator, AssetLoadRequest request) {
        if (HighDynamicRangeImage.isHighDynamicRange(locator.resolvedPath(request.uri()))) {
            return Optional.empty();
        }
        return request.source(locator).flatMap(TextureAssetLoader::readAllBytes);
    }

    private static Optional<Object> readAllBytes(AssetSource source) {
        try (InputStream stream = source.open().orElse(null)) {
            return stream == null ? Optional.empty() : Optional.of(stream.readAllBytes());
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    @Override
    public TextureHandle loadFromRead(EngineServices services, AssetLoadRequest request, Object read) {
        AssetLocator locator = services.assets().locator();
        try {
            TextureImportSettings settings =
                    TextureImportSettings.from(request.settings(locator), request.variant());
            return Texture2D.loadFromEncodedBytes(services.renderBackend(), (byte[]) read,
                    settings.format(), settings.wrap(), settings.filter(), Texture2D.samplingOf(settings));
        } catch (RuntimeException error) {
            services.logger().error("[TextureAssetLoader] Failed to upload " + request.uri(), error);
            return null;
        }
    }

    @Override
    public void dispose(EngineServices services, TextureHandle value) {
        services.renderBackend().destroy(value);
    }
}
