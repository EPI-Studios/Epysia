package fr.epistudio.epysia.diagnostics;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.Hud;

import java.util.Locale;

public final class FrameRateOverlay implements GameSystem {

    private static final float SAMPLE_SECONDS = 0.25f;
    private static final float OVERLAY_X = 8.0f;
    private static final float OVERLAY_Y = 8.0f;
    private static final float MILLIS_PER_SECOND = 1000.0f;

    private Hud hud;
    private float elapsedSeconds;
    private int framesInSample;
    private String label = "";

    @Override
    public void initialize(EngineServices services) {
        hud = services.hud();
    }

    @Override
    public void update(Scene scene, InputState input, float deltaTimeSeconds) {
        if (hud == null) {
            return;
        }
        accumulate(deltaTimeSeconds);
        if (!label.isEmpty()) {
            hud.text(OVERLAY_X, OVERLAY_Y, label);
        }
    }

    private void accumulate(float deltaTimeSeconds) {
        elapsedSeconds += deltaTimeSeconds;
        framesInSample++;
        if (elapsedSeconds < SAMPLE_SECONDS) {
            return;
        }
        float average = framesInSample / elapsedSeconds;
        label = String.format(Locale.ROOT, "%.0f FPS  (%.2f ms)", average, MILLIS_PER_SECOND / average);
        elapsedSeconds = 0.0f;
        framesInSample = 0;
    }
}
