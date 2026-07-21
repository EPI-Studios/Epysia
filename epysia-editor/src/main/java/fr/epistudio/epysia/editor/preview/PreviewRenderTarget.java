package fr.epistudio.epysia.editor.preview;

import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;

import java.util.List;
import java.util.Optional;

public final class PreviewRenderTarget {

    private final int width;
    private final int height;
    private final TextureHandle colorTexture;
    private final TextureHandle depthTexture;
    private final RenderTargetHandle renderTarget;
    private final int glTextureName;

    private PreviewRenderTarget(int width, int height, TextureHandle colorTexture,
                                TextureHandle depthTexture, RenderTargetHandle renderTarget,
                                int glTextureName) {
        this.width = width;
        this.height = height;
        this.colorTexture = colorTexture;
        this.depthTexture = depthTexture;
        this.renderTarget = renderTarget;
        this.glTextureName = glTextureName;
    }

    public static PreviewRenderTarget create(OpenGlRenderBackend backend, int width, int height) {
        TextureHandle color = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        TextureHandle depth = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT, SamplerFilter.LINEAR));
        RenderTargetHandle target = backend.createRenderTarget(new RenderTargetDescriptor(width, height,
                List.of(color), Optional.of(depth)));
        return new PreviewRenderTarget(width, height, color, depth, target, backend.glTextureName(color));
    }

    public void destroy(OpenGlRenderBackend backend) {
        backend.destroy(renderTarget);
        backend.destroy(colorTexture);
        backend.destroy(depthTexture);
    }

    public RenderTargetHandle handle() {
        return renderTarget;
    }

    public int glTextureName() {
        return glTextureName;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
