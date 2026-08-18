package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.procedural.CurveTextureLoader;
import fr.epistudio.epysia.assets.procedural.GeneratedTexture;
import fr.epistudio.epysia.assets.procedural.GradientTextureLoader;
import fr.epistudio.epysia.assets.procedural.ProceduralNoise;
import fr.epistudio.epysia.editor.ui.ProceduralDocumentModel;
import fr.epistudio.epysia.editor.ui.ProceduralTextureSection;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureWrap;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;

public final class ProceduralTexturePreview implements ProceduralTextureSection.ProceduralPreview {

    private static final int PREVIEW_SIZE = 192;

    private final OpenGlRenderBackend backend;
    private String cachedPath = "";
    private String cachedDocument = "";
    private TextureHandle cachedHandle;

    public ProceduralTexturePreview(OpenGlRenderBackend backend) {
        this.backend = backend;
    }

    @Override
    public Optional<Integer> textureFor(Path path, ProceduralDocumentModel model) {
        String key = path.toAbsolutePath().toString();
        String document = model.toJson();
        if (key.equals(cachedPath) && document.equals(cachedDocument) && cachedHandle != null) {
            return Optional.of(backend.glTextureName(cachedHandle));
        }
        release();
        cachedPath = key;
        cachedDocument = document;
        cachedHandle = generate(model);
        return cachedHandle == null ? Optional.empty() : Optional.of(backend.glTextureName(cachedHandle));
    }

    private TextureHandle generate(ProceduralDocumentModel model) {
        return switch (model.kind()) {
            case NOISE -> uploadNoise(model);
            case GRADIENT -> upload(GradientTextureLoader.paint(model.gradient(), PREVIEW_SIZE),
                    PREVIEW_SIZE, 1);
            case CURVE -> upload(CurveTextureLoader.paint(model.curve(), PREVIEW_SIZE), PREVIEW_SIZE, 1);
        };
    }

    private TextureHandle uploadNoise(ProceduralDocumentModel model) {
        ByteBuffer surface = GeneratedTexture.surface(PREVIEW_SIZE, PREVIEW_SIZE);
        float step = (float) model.period() / PREVIEW_SIZE;
        for (int y = 0; y < PREVIEW_SIZE; y++) {
            for (int x = 0; x < PREVIEW_SIZE; x++) {
                float sample = sampleOf(model, x * step, y * step);
                float level = model.inverted() ? 1.0f - sample : sample;
                GeneratedTexture.write(surface, y * PREVIEW_SIZE + x, level, level, level, 1.0f);
            }
        }
        return upload(surface, PREVIEW_SIZE, PREVIEW_SIZE);
    }

    private static float sampleOf(ProceduralDocumentModel model, float x, float y) {
        return switch (model.noiseKindName()) {
            case "VALUE" -> ProceduralNoise.value(model.seed(), x, y, model.period());
            case "CELLULAR" -> ProceduralNoise.cellular(model.seed(), x, y, model.period());
            default -> ProceduralNoise.fractal(model.seed(), x, y, model.octaves(),
                    model.lacunarity(), model.gain(), model.period());
        };
    }

    private TextureHandle upload(ByteBuffer surface, int width, int height) {
        return GeneratedTexture.upload(backend, width, height, surface,
                TextureWrap.CLAMP_TO_EDGE, SamplerFilter.LINEAR);
    }

    @Override
    public void invalidate(Path path) {
        if (path.toAbsolutePath().toString().equals(cachedPath)) {
            release();
        }
    }

    public void dispose() {
        release();
    }

    private void release() {
        if (cachedHandle != null) {
            backend.destroy(cachedHandle);
            cachedHandle = null;
        }
        cachedPath = "";
        cachedDocument = "";
    }
}
