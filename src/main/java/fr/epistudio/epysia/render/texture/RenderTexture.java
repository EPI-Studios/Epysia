package fr.epistudio.epysia.render.texture;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Optional;

public final class RenderTexture {

    private static final int CHANNELS = 4;

    private final TextureHandle color;
    private final TextureHandle depth;
    private final RenderTargetHandle target;
    private final int width;
    private final int height;

    private RenderTexture(TextureHandle color, TextureHandle depth, RenderTargetHandle target,
                          int width, int height) {
        this.color = color;
        this.depth = depth;
        this.target = target;
        this.width = width;
        this.height = height;
    }

    public static RenderTexture create(EngineServices services, int width, int height) {
        return create(services.renderBackend(), width, height);
    }

    public static RenderTexture create(RenderBackend backend, int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        TextureHandle color = backend.createTexture(new TextureDescriptor(safeWidth, safeHeight,
                TextureFormat.RGBA16F, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        TextureHandle depth = backend.createTexture(new TextureDescriptor(safeWidth, safeHeight,
                TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT));
        RenderTargetHandle target = backend.createRenderTarget(new RenderTargetDescriptor(
                safeWidth, safeHeight, List.of(color), Optional.of(depth)));
        return new RenderTexture(color, depth, target, safeWidth, safeHeight);
    }

    public TextureHandle texture() {
        return color;
    }

    public RenderTargetHandle target() {
        return target;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixelFloatCount() {
        return width * height * CHANNELS;
    }

    public FloatBuffer read(EngineServices services, FloatBuffer destination) {
        services.renderBackend().readTextureLevel(color, 0, destination);
        return destination;
    }

    public void destroy(EngineServices services) {
        destroy(services.renderBackend());
    }

    public void destroy(RenderBackend backend) {
        backend.destroy(target);
        backend.destroy(color);
        backend.destroy(depth);
    }
}
