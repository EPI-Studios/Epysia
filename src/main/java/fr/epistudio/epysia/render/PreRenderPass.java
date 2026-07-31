package fr.epistudio.epysia.render;

@FunctionalInterface
public interface PreRenderPass {

    void capture(SceneCapture capture, float interpolationAlpha);
}
