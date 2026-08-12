package fr.epistudio.epysia.render.backend;

public interface RenderSurface {
    int framebufferWidth();

    int framebufferHeight();

    default long nativeWindowHandle() {
        return 0L;
    }

    default boolean vsyncRequested() {
        return true;
    }
}
