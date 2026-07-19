package fr.epistudio.epysia.render.backend;

import java.util.List;
import java.util.Optional;

public record RenderTargetDescriptor(
        int width,
        int height,
        List<TextureHandle> colorAttachments,
        Optional<TextureHandle> depthAttachment,
        int colorLayer,
        int colorMipLevel,
        int depthLayer
) {

    public static final int NO_LAYER = -1;

    public RenderTargetDescriptor {
        colorAttachments = List.copyOf(colorAttachments);
    }

    public RenderTargetDescriptor(int width, int height, List<TextureHandle> colorAttachments,
                                  Optional<TextureHandle> depthAttachment) {
        this(width, height, colorAttachments, depthAttachment, NO_LAYER, 0, NO_LAYER);
    }

    public static RenderTargetDescriptor cubeFace(int size, TextureHandle cubemap, int face, int mipLevel) {
        return new RenderTargetDescriptor(size, size, List.of(cubemap), Optional.empty(), face, mipLevel, NO_LAYER);
    }

    public static RenderTargetDescriptor depthArrayLayer(int size, TextureHandle depthArray, int layer) {
        return new RenderTargetDescriptor(size, size, List.of(), Optional.of(depthArray), NO_LAYER, 0, layer);
    }
}
