package fr.epistudio.epysia.assets.procedural;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.assets.AssetLoadRequest;
import fr.epistudio.epysia.assets.AssetLoader;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureWrap;
import fr.epistudio.epysia.vfx.lut.VfxCurve;

import java.nio.ByteBuffer;
import java.util.Map;

public final class CurveTextureLoader implements AssetLoader<TextureHandle> {

    public static final String EXTENSION = ".epycurve";
    public static final String CURVE_KEY = "curve";
    public static final String WIDTH_KEY = "width";
    public static final int DEFAULT_WIDTH = 256;

    private static final int MAXIMUM_WIDTH = 4096;

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
        VfxCurve curve = VfxCurve.decode(ProceduralDocument.stringOf(document, CURVE_KEY, ""));
        return GeneratedTexture.upload(services.renderBackend(), width, 1, paint(curve, width),
                TextureWrap.CLAMP_TO_EDGE, SamplerFilter.LINEAR);
    }

    public static ByteBuffer paint(VfxCurve curve, int width) {
        float[] samples = curve.sample(width);
        ByteBuffer surface = GeneratedTexture.surface(width, 1);
        for (int index = 0; index < width; index++) {
            float level = samples[index];
            GeneratedTexture.write(surface, index, level, level, level, 1.0f);
        }
        return surface;
    }

    @Override
    public void dispose(EngineServices services, TextureHandle value) {
        services.renderBackend().destroy(value);
    }
}
