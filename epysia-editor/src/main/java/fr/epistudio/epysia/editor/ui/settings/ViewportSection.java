package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.editor.ui.kit.Sliders;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;

public final class ViewportSection {

    private static final float MIN_CAMERA_SPEED = 0.5f;
    private static final float MAX_CAMERA_SPEED = 100.0f;
    private static final float MIN_CAMERA_BOOST = 1.0f;
    private static final float MAX_CAMERA_BOOST = 20.0f;
    private static final float DEPTH_RATIO_WARNING = 20000.0f;

    public interface TuningListener {
        void onViewportTuningChanged(float overlayThickness, float gridFadeDistance);
    }

    private final SettingsChrome chrome;
    private final TuningListener tuningListener;
    private final float[] cameraSpeed = new float[1];
    private final float[] cameraBoost = new float[1];
    private final float[] lookSensitivity = new float[1];
    private final float[] sceneNear = new float[1];
    private final float[] sceneFar = new float[1];
    private final float[] sceneFieldOfView = new float[1];
    private final float[] overlayThickness = new float[1];
    private final float[] gridFadeDistance = new float[1];

    private boolean invertLookY;

    public ViewportSection(SettingsChrome chrome, TuningListener tuningListener) {
        this.chrome = chrome;
        this.tuningListener = tuningListener;
    }

    public void load(EditorPreferences preferences) {
        cameraSpeed[0] = preferences.cameraSpeed();
        cameraBoost[0] = preferences.cameraBoost();
        lookSensitivity[0] = preferences.lookSensitivity();
        invertLookY = preferences.invertLookY();
        sceneNear[0] = preferences.sceneNear();
        sceneFar[0] = preferences.sceneFar();
        sceneFieldOfView[0] = preferences.sceneFieldOfView();
        overlayThickness[0] = preferences.overlayThickness();
        gridFadeDistance[0] = preferences.gridFadeDistance();
    }

    public float cameraSpeed() {
        return cameraSpeed[0];
    }

    public float cameraBoost() {
        return cameraBoost[0];
    }

    public float lookSensitivity() {
        return lookSensitivity[0];
    }

    public boolean invertLookY() {
        return invertLookY;
    }

    public float sceneNear() {
        return sceneNear[0];
    }

    public float sceneFar() {
        return sceneFar[0];
    }

    public float sceneFieldOfView() {
        return sceneFieldOfView[0];
    }

    public float overlayThickness() {
        return overlayThickness[0];
    }

    public float gridFadeDistance() {
        return gridFadeDistance[0];
    }

    public void renderCamera() {
        chrome.row("Move speed", () -> ImGui.dragFloat("##value", cameraSpeed, 0.1f,
                MIN_CAMERA_SPEED, MAX_CAMERA_SPEED));
        chrome.row("Boost multiplier", () -> ImGui.dragFloat("##value", cameraBoost, 0.1f,
                MIN_CAMERA_BOOST, MAX_CAMERA_BOOST));
        chrome.row("Look sensitivity", () -> ImGui.dragFloat("##value", lookSensitivity, 0.0001f,
                EditorPreferences.MIN_LOOK_SENSITIVITY, EditorPreferences.MAX_LOOK_SENSITIVITY, "%.4f"));
        invertLookY = chrome.toggleRow("Invert look Y", invertLookY);
    }

    public void renderSceneView() {
        chrome.row("Near plane", () -> ImGui.dragFloat("##value", sceneNear, 0.01f,
                EditorPreferences.MIN_SCENE_NEAR, EditorPreferences.MAX_SCENE_NEAR, "%.3f"));
        chrome.row("Far plane", () -> ImGui.dragFloat("##value", sceneFar, 5.0f,
                EditorPreferences.MIN_SCENE_FAR, EditorPreferences.MAX_SCENE_FAR, "%.0f"));
        chrome.row("Field of view", () -> ImGui.dragFloat("##value", sceneFieldOfView, 0.5f,
                EditorPreferences.MIN_SCENE_FIELD_OF_VIEW, EditorPreferences.MAX_SCENE_FIELD_OF_VIEW, "%.1f"));
        if (sceneFar[0] / Math.max(sceneNear[0], EditorPreferences.MIN_SCENE_NEAR) > DEPTH_RATIO_WARNING) {
            chrome.hint(TextKey.EDITOR_SETTINGS_DIALOG_NEAR_PLANE_HELP);
        }
    }

    public void renderViewport() {
        boolean changed = renderSlider("Overlay line thickness",
                TextKey.EDITOR_SETTINGS_DIALOG_OVERLAY_THICKNESS, "##overlay-thickness",
                overlayThickness, EditorPreferences.MIN_OVERLAY_THICKNESS,
                EditorPreferences.MAX_OVERLAY_THICKNESS);
        changed |= renderSlider("Grid fade distance",
                TextKey.EDITOR_SETTINGS_DIALOG_GRID_FADE, "##grid-fade-distance",
                gridFadeDistance, EditorPreferences.MIN_GRID_FADE_DISTANCE,
                EditorPreferences.MAX_GRID_FADE_DISTANCE);
        if (changed) {
            tuningListener.onViewportTuningChanged(overlayThickness[0], gridFadeDistance[0]);
        }
    }

    private boolean renderSlider(String label, TextKey caption, String id, float[] value,
                                 float minimum, float maximum) {
        if (!chrome.accepts(label)) {
            return false;
        }
        ImGui.pushID(label);
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(I18n.translate(caption));
        ImGui.sameLine(chrome.labelColumnWidth());
        ImGui.setNextItemWidth(-1.0f);
        boolean changed = Sliders.filled(id, value, minimum, maximum, chrome.sliderWidth());
        ImGui.popID();
        return changed;
    }
}
