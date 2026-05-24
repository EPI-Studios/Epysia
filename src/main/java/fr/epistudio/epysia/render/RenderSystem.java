package fr.epistudio.epysia.render;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.scene.Scene;

public interface RenderSystem {

    void initialize(RenderBackend backend, StageConfigurer configurer);

    void collect(Scene scene, FrameBuilder frame, float interpolationAlpha);

    default void onResize(RenderBackend backend, StageConfigurer configurer, int width, int height) {
    }

    void shutdown(RenderBackend backend);
}
