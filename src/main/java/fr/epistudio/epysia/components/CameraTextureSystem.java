package fr.epistudio.epysia.components;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.render.texture.RenderTexture;
import fr.epistudio.epysia.scene.Scene;

import java.util.List;

public final class CameraTextureSystem implements GameSystem {

    private static final float SNAPSHOT_ALPHA = 1.0f;

    private EngineServices services;
    private EpysiaEngine engine;
    private boolean capturing;

    @Override
    public void initialize(EngineServices engineServices) {
        services = engineServices;
        engine = engineServices instanceof EpysiaEngine actual ? actual : null;
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        if (engine == null || capturing) {
            return;
        }
        capturing = true;
        try {
            for (CameraTexture cameraTexture : scene.componentsOf(CameraTexture.class)) {
                capture(cameraTexture);
            }
        } finally {
            capturing = false;
        }
    }

    private void capture(CameraTexture cameraTexture) {
        if (!cameraTexture.enabled() || cameraTexture.owner().isEmpty()) {
            return;
        }
        Camera3D camera = cameraTexture.owner().get().getComponentOrNull(Camera3D.class);
        if (camera == null || !cameraTexture.consumeCaptureRequest()) {
            return;
        }
        RenderTexture target = cameraTexture.ensureTexture(services);
        engine.render(List.of(camera), target.target(), SNAPSHOT_ALPHA);
    }
}
