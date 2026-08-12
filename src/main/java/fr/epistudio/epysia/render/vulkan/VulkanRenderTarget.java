package fr.epistudio.epysia.render.vulkan;

import java.util.List;
import java.util.Optional;

public record VulkanRenderTarget(
        int width,
        int height,
        List<VulkanTexture> colorAttachments,
        List<Long> colorViews,
        Optional<VulkanTexture> depthAttachment,
        Optional<Long> depthView,
        RenderingFormats formats,
        int colorLayer,
        int depthLayer
) {

    public VulkanRenderTarget {
        colorAttachments = List.copyOf(colorAttachments);
        colorViews = List.copyOf(colorViews);
    }
}
