package fr.epistudio.epysia.render;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;

import java.util.List;

public interface SceneCapture {

    void renderTo(List<Camera3D> cameras, RenderTargetHandle target, float interpolationAlpha);
}
