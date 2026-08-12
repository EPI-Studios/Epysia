package fr.epistudio.epysia.render.vulkan;

import java.util.List;

public record RenderingFormats(List<Integer> colorFormats, int depthFormat, int stencilFormat) {

    public RenderingFormats {
        colorFormats = List.copyOf(colorFormats);
    }

    public boolean hasDepth() {
        return depthFormat != 0;
    }

    public boolean hasStencil() {
        return stencilFormat != 0;
    }
}
