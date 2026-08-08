package fr.epistudio.epysia.render;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;

import java.util.List;
import java.util.Optional;

public record RenderContext(List<Camera3D> activeCameras,
                            RenderTargetHandle primaryTarget,
                            float interpolationAlpha,
                            long animationGeneration) {
    public RenderContext {
        activeCameras = List.copyOf(activeCameras);
    }

    public static RenderContext of(List<Camera3D> cameras, RenderTargetHandle target,
                                   float interpolationAlpha, long animationGeneration) {
        return new RenderContext(cameras, target, interpolationAlpha, animationGeneration);
    }

    public Optional<Camera3D> primaryCamera() {
        return activeCameras.isEmpty() ? Optional.empty() : Optional.of(activeCameras.get(0));
    }
}
