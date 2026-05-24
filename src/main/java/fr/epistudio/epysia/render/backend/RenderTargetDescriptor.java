package fr.epistudio.epysia.render.backend;

import java.util.List;
import java.util.Optional;

public record RenderTargetDescriptor(
        int width,
        int height,
        List<TextureHandle> colorAttachments,
        Optional<TextureHandle> depthAttachment
) {

    public RenderTargetDescriptor {
        colorAttachments = List.copyOf(colorAttachments);
    }
}
