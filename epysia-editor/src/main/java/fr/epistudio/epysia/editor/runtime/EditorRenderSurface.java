package fr.epistudio.epysia.editor.runtime;

import fr.epistudio.epysia.render.backend.RenderSurface;

public final class EditorRenderSurface implements RenderSurface {

    private int width = 16;
    private int height = 16;

    public void setSize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    @Override
    public int framebufferWidth() {
        return width;
    }

    @Override
    public int framebufferHeight() {
        return height;
    }
}
