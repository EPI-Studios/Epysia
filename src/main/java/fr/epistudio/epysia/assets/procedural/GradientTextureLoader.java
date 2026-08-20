package fr.epistudio.epysia.assets.procedural;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureWrap;
import fr.epistudio.epysia.vfx.lut.VfxGradient;

import java.nio.ByteBuffer;
import java.util.Map;

public final class GradientTextureLoader implements AssetLoader<TextureHandle> {

    public static final String EXTENSION = ".epygradient";
    public static final String GRADIENT_KEY = "gradient";
    public static final String WIDTH_KEY = "width";
    public static final String VERTICAL_KEY = "vertical";
    public static final int DEFAULT_WIDTH = 256;

    private static final int MAXIMUM_WIDTH = 4096;
    private static final int CHANNELS = 4;

    @Override
    public Class<TextureHandle> assetType() {
        return TextureHandle.class;
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{EXTENSION};
    }

    @Override
    public TextureHandle load(EngineServices services, AssetLoadRequest request) {
        Map<String, Object> document = ProceduralDocument.read(services.assets().locator(), request);
        int width = Math.clamp(ProceduralDocument.intOf(document, WIDTH_KEY, DEFAULT_WIDTH), 2, MAXIMUM_WIDTH);
        boolean vertical = ProceduralDocument.booleanOf(document, VERTICAL_KEY);
        VfxGradient gradient = VfxGradient.decode(ProceduralDocument.stringOf(document, GRADIENT_KEY, ""));
        ByteBuffer surface = paint(gradient, width);
        int surfaceWidth = vertical ? 1 : width;
        int surfaceHeight = vertical ? width : 1;
        return GeneratedTexture.upload(services.renderBackend(), surfaceWidth, surfaceHeight, surface,
                TextureWrap.CLAMP_TO_EDGE, SamplerFilter.LINEAR);
    }

    public static ByteBuffer paint(VfxGradient gradient, int width) {
        float[] samples = gradient.sample(width);
        ByteBuffer surface = GeneratedTexture.surface(width, 1);
        for (int index = 0; index < width; index++) {
            int base = index * CHANNELS;
            GeneratedTexture.write(surface, index, samples[base], samples[base + 1],
                    samples[base + 2], samples[base + 3]);
        }
        return surface;
    }

    @Override
    public void dispose(EngineServices services, TextureHandle value) {
        services.renderBackend().destroy(value);
    }
}
