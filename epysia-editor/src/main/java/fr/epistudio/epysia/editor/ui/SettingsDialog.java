package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.preferences.EditorPreferences;
import fr.epistudio.epysia.gpu.GpuPreference;
import fr.epistudio.epysia.project.EditorSettings;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.render.environment.SkySettings;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.postfx.PostEffectStack;
import fr.epistudio.epysia.render.postfx.PostProcessSettings;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SettingsDialog {

    private static final String POPUP_TITLE = "Settings";
    private static final float DIALOG_WIDTH = 720.0f;
    private static final float DIALOG_HEIGHT = 560.0f;
    private static final int LAYER_NAME_CAPACITY = 64;
    private static final float MIN_CAMERA_SPEED = 0.5f;
    private static final float MAX_CAMERA_SPEED = 100.0f;
    private static final float MIN_CAMERA_BOOST = 1.0f;
    private static final float MAX_CAMERA_BOOST = 20.0f;
    private static final int MIN_AUTOSAVE_SECONDS = 10;
    private static final int MAX_AUTOSAVE_SECONDS = 3600;
    private static final float MIN_INTENSITY = 0.0f;
    private static final float MAX_INTENSITY = 5.0f;
    private static final float MIN_EXPOSURE = 0.1f;
    private static final float MAX_EXPOSURE = 4.0f;
    private static final float MIN_OCCLUSION_RADIUS = 0.1f;
    private static final float MAX_OCCLUSION_RADIUS = 3.0f;
    private static final float MIN_SHADOW_DISTANCE = 10.0f;
    private static final float MAX_SHADOW_DISTANCE = 500.0f;
    private static final float DEFAULT_SHADOW_DISTANCE = 60.0f;

    public interface ViewportTuningListener {
        void onViewportTuningChanged(float overlayThickness, float gridFadeDistance);
    }

    private final Consumer<EditorSettings> onSettingsSaved;
    private final Consumer<EditorPreferences> onPreferencesSaved;
    private final ViewportTuningListener viewportTuningListener;
    private final ImString[] layerNames = new ImString[EditorSettings.LAYER_COUNT];
    private final float[] cameraSpeed = new float[1];
    private final float[] cameraBoost = new float[1];
    private final int[] autosaveInterval = new int[1];
    private final float[] overlayThickness = new float[1];
    private final float[] gridFadeDistance = new float[1];
    private final GpuPreference[] gpuPreferences = GpuPreference.values();
    private int selectedGpuIndex;
    private final float[] skyIntensity = new float[1];
    private final float[] ambientIntensity = new float[1];
    private final float[] exposure = new float[1];
    private final float[] vignette = new float[1];
    private final float[] bloomIntensity = new float[1];
    private final float[] ambientOcclusionIntensity = new float[1];
    private final float[] ambientOcclusionRadius = new float[1];
    private final float[] shadowDistance = new float[1];
    private int[] collisionMatrix = new int[EditorSettings.LAYER_COUNT];
    private boolean autosaveEnabled;
    private boolean detachableWindows;
    private boolean lightCullingEnabled = true;
    private boolean bloomEnabled;
    private boolean ambientOcclusionEnabled;
    private boolean antiAliasEnabled;
    private EditorPreferences basePreferences = EditorPreferences.defaults();
    private Optional<PostProcessSettings> postProcessSettings = Optional.empty();
    private Optional<SkySettings> skySettings = Optional.empty();
    private Optional<MeshRenderSystem> meshRenderSystem = Optional.empty();
    private Optional<PostEffectsSection> postEffectsSection = Optional.empty();
    private Optional<Supplier<PostEffectStack>> globalPostEffectStack = Optional.empty();
    private Runnable onPostEffectsChanged = () -> {
    };
    private Project project;
    private boolean openRequested;

    public SettingsDialog(Consumer<EditorSettings> onSettingsSaved,
                          Consumer<EditorPreferences> onPreferencesSaved,
                          ViewportTuningListener viewportTuningListener) {
        this.onSettingsSaved = onSettingsSaved;
        this.onPreferencesSaved = onPreferencesSaved;
        this.viewportTuningListener = viewportTuningListener;
        for (int i = 0; i < layerNames.length; i++) {
            layerNames[i] = new ImString(LAYER_NAME_CAPACITY);
        }
        shadowDistance[0] = DEFAULT_SHADOW_DISTANCE;
    }

    public void attachRenderTuning(PostProcessSettings postProcess, SkySettings sky, MeshRenderSystem meshSystem) {
        postProcessSettings = Optional.of(postProcess);
        skySettings = Optional.of(sky);
        meshRenderSystem = Optional.of(meshSystem);
    }

    public void attachPostEffects(PostEffectsSection section, Supplier<PostEffectStack> stack, Runnable onChanged) {
        postEffectsSection = Optional.of(section);
        globalPostEffectStack = Optional.of(stack);
        onPostEffectsChanged = onChanged;
    }

    public void openFor(EditorSettings settings, EditorPreferences preferences, Project openedProject) {
        collisionMatrix = settings.collisionMatrix();
        for (int i = 0; i < layerNames.length; i++) {
            layerNames[i].set(settings.layerNames().get(i));
        }
        loadPreferences(preferences);
        project = openedProject;
        loadRenderTuning();
        openRequested = true;
    }

    private void loadPreferences(EditorPreferences preferences) {
        basePreferences = preferences;
        cameraSpeed[0] = preferences.cameraSpeed();
        cameraBoost[0] = preferences.cameraBoost();
        autosaveInterval[0] = preferences.autosaveIntervalSeconds();
        autosaveEnabled = preferences.autosaveEnabled();
        detachableWindows = preferences.detachableWindows();
        overlayThickness[0] = preferences.overlayThickness();
        gridFadeDistance[0] = preferences.gridFadeDistance();
        selectedGpuIndex = indexOf(preferences.gpuPreference());
    }

    private int indexOf(GpuPreference preference) {
        for (int index = 0; index < gpuPreferences.length; index++) {
            if (gpuPreferences[index] == preference) {
                return index;
            }
        }
        return 0;
    }

    private void loadRenderTuning() {
        if (postProcessSettings.isEmpty() || skySettings.isEmpty()) {
            return;
        }
        PostProcessSettings postProcess = postProcessSettings.get();
        skyIntensity[0] = skySettings.get().skyIntensity();
        ambientIntensity[0] = skySettings.get().ambientIntensity();
        exposure[0] = postProcess.gradeExposure();
        vignette[0] = postProcess.vignetteStrength();
        bloomIntensity[0] = postProcess.bloomIntensity();
        ambientOcclusionIntensity[0] = postProcess.ambientOcclusionIntensity();
        ambientOcclusionRadius[0] = postProcess.ambientOcclusionRadius();
        bloomEnabled = postProcess.bloomEnabled();
        ambientOcclusionEnabled = postProcess.ambientOcclusionEnabled();
        antiAliasEnabled = postProcess.antiAliasingEnabled();
        meshRenderSystem.ifPresent(system -> lightCullingEnabled = system.clusteringEnabled());
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_TITLE);
            openRequested = false;
        }
        ImGui.setNextWindowSize(DIALOG_WIDTH, DIALOG_HEIGHT, ImGuiCond.Appearing);
        if (!ImGui.beginPopupModal(POPUP_TITLE)) {
            return;
        }
        renderTabs();
        renderFooter();
        ImGui.endPopup();
    }

    private void renderTabs() {
        if (!ImGui.beginTabBar("##settings-tabs")) {
            return;
        }
        renderCollisionTab();
        renderEditorTab();
        renderRenderingTab();
        renderProjectTab();
        ImGui.endTabBar();
    }

    private void renderCollisionTab() {
        if (!ImGui.beginTabItem("Collision")) {
            return;
        }
        ImGui.beginChild("##collision-grid", 0.0f, -ImGui.getFrameHeightWithSpacing());
        for (int row = 0; row < EditorSettings.LAYER_COUNT; row++) {
            renderCollisionRow(row);
        }
        ImGui.endChild();
        ImGui.endTabItem();
    }

    private void renderCollisionRow(int row) {
        ImGui.pushID(row);
        ImGui.setNextItemWidth(150.0f);
        ImGui.inputText("##layer-name", layerNames[row]);
        for (int column = 0; column < EditorSettings.LAYER_COUNT; column++) {
            ImGui.sameLine();
            renderCollisionCell(row, column);
        }
        ImGui.popID();
    }

    private void renderCollisionCell(int row, int column) {
        ImGui.pushID(column);
        boolean checked = (collisionMatrix[row] & (1 << column)) != 0;
        if (ImGui.checkbox("##cell", checked)) {
            toggleCollision(row, column, !checked);
        }
        ImGui.popID();
    }

    private void toggleCollision(int row, int column, boolean enabled) {
        if (enabled) {
            collisionMatrix[row] |= (1 << column);
            collisionMatrix[column] |= (1 << row);
        } else {
            collisionMatrix[row] &= ~(1 << column);
            collisionMatrix[column] &= ~(1 << row);
        }
    }

    private void renderEditorTab() {
        if (!ImGui.beginTabItem("Editor")) {
            return;
        }
        ImGui.dragFloat("Camera speed", cameraSpeed, 0.1f, MIN_CAMERA_SPEED, MAX_CAMERA_SPEED);
        ImGui.dragFloat("Camera boost multiplier", cameraBoost, 0.1f, MIN_CAMERA_BOOST, MAX_CAMERA_BOOST);
        if (ImGui.checkbox("Detachable windows (multi-monitor, restart required)", detachableWindows)) {
            detachableWindows = !detachableWindows;
        }
        if (ImGui.checkbox("Autosave scenes", autosaveEnabled)) {
            autosaveEnabled = !autosaveEnabled;
        }
        if (autosaveEnabled) {
            ImGui.dragInt("Autosave interval (seconds)", autosaveInterval, 1.0f,
                    MIN_AUTOSAVE_SECONDS, MAX_AUTOSAVE_SECONDS);
        }
        renderViewportSection();
        renderGpuSection();
        ImGui.endTabItem();
    }

    private void renderGpuSection() {
        ImGui.separator();
        ImGui.text("Graphics");
        ImGui.setNextItemWidth(220.0f);
        if (ImGui.beginCombo("Preferred GPU", gpuPreferences[selectedGpuIndex].displayName())) {
            for (int index = 0; index < gpuPreferences.length; index++) {
                if (ImGui.selectable(gpuPreferences[index].displayName(), index == selectedGpuIndex)) {
                    selectedGpuIndex = index;
                }
            }
            ImGui.endCombo();
        }
        ImGui.textDisabled("Applies to launched and exported games now, and to the editor after a restart.");
    }

    private void renderViewportSection() {
        ImGui.separator();
        ImGui.text("Viewport");
        boolean thicknessChanged = ImGui.sliderFloat("Overlay line thickness", overlayThickness,
                EditorPreferences.MIN_OVERLAY_THICKNESS, EditorPreferences.MAX_OVERLAY_THICKNESS);
        boolean fadeChanged = ImGui.sliderFloat("Grid fade distance", gridFadeDistance,
                EditorPreferences.MIN_GRID_FADE_DISTANCE, EditorPreferences.MAX_GRID_FADE_DISTANCE);
        if (thicknessChanged || fadeChanged) {
            viewportTuningListener.onViewportTuningChanged(overlayThickness[0], gridFadeDistance[0]);
        }
    }

    private void renderRenderingTab() {
        if (!ImGui.beginTabItem("Rendering")) {
            return;
        }
        if (postProcessSettings.isEmpty()) {
            ImGui.textDisabled("Rendering settings become available once a scene viewport is open.");
        } else {
            renderRenderingControls();
        }
        ImGui.endTabItem();
    }

    private void renderRenderingControls() {
        ImGui.dragFloat("Sky intensity", skyIntensity, 0.02f, MIN_INTENSITY, MAX_INTENSITY);
        ImGui.dragFloat("Ambient intensity", ambientIntensity, 0.02f, MIN_INTENSITY, MAX_INTENSITY);
        ImGui.dragFloat("Exposure", exposure, 0.02f, MIN_EXPOSURE, MAX_EXPOSURE);
        ImGui.dragFloat("Vignette", vignette, 0.01f, 0.0f, 1.0f);
        ImGui.dragFloat("Shadow distance", shadowDistance, 1.0f, MIN_SHADOW_DISTANCE, MAX_SHADOW_DISTANCE);
        if (ImGui.checkbox("GPU light culling", lightCullingEnabled)) {
            lightCullingEnabled = !lightCullingEnabled;
        }
        renderToggleRows();
    }

    private void renderToggleRows() {
        if (ImGui.checkbox("Bloom", bloomEnabled)) {
            bloomEnabled = !bloomEnabled;
        }
        if (bloomEnabled) {
            ImGui.dragFloat("Bloom intensity", bloomIntensity, 0.02f, MIN_INTENSITY, MAX_INTENSITY);
        }
        if (ImGui.checkbox("Ambient occlusion", ambientOcclusionEnabled)) {
            ambientOcclusionEnabled = !ambientOcclusionEnabled;
        }
        if (ambientOcclusionEnabled) {
            ImGui.dragFloat("Occlusion intensity", ambientOcclusionIntensity, 0.02f, MIN_INTENSITY, MAX_INTENSITY);
            ImGui.dragFloat("Occlusion radius", ambientOcclusionRadius, 0.02f, MIN_OCCLUSION_RADIUS, MAX_OCCLUSION_RADIUS);
        }
        if (ImGui.checkbox("Anti-aliasing (FXAA)", antiAliasEnabled)) {
            antiAliasEnabled = !antiAliasEnabled;
        }
        renderPostEffectsSection();
    }

    private void renderPostEffectsSection() {
        if (postEffectsSection.isEmpty() || globalPostEffectStack.isEmpty()) {
            return;
        }
        ImGui.separator();
        ImGui.text("Post Effects");
        ImGui.textDisabled("Scene-wide stack, applied live. Cameras can override it in the Inspector.");
        postEffectsSection.get().render(globalPostEffectStack.get().get(), onPostEffectsChanged);
    }

    private void renderProjectTab() {
        if (!ImGui.beginTabItem("Project")) {
            return;
        }
        if (project != null) {
            ImGui.labelText("Project name", project.name());
            ImGui.labelText("Engine version", project.engineVersion());
            ImGui.labelText("Root directory", project.rootDirectory().toString());
            ImGui.labelText("Default scene", project.defaultScenePath().getFileName().toString());
        }
        ImGui.endTabItem();
    }

    private void renderFooter() {
        ImGui.separator();
        if (ImGui.button("Save")) {
            save();
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
    }

    private void save() {
        applyRenderTuning();
        onSettingsSaved.accept(buildSettings());
        onPreferencesSaved.accept(new EditorPreferences(cameraSpeed[0], cameraBoost[0],
                autosaveEnabled, autosaveInterval[0],
                basePreferences.gridVisible(), basePreferences.snapEnabled(),
                overlayThickness[0], gridFadeDistance[0], gpuPreferences[selectedGpuIndex], detachableWindows,
                basePreferences.shaderNodePreviewsEnabled()));
    }

    private EditorSettings buildSettings() {
        List<String> names = new ArrayList<>(layerNames.length);
        for (ImString layerName : layerNames) {
            names.add(layerName.get());
        }
        return new EditorSettings(names, collisionMatrix);
    }

    private void applyRenderTuning() {
        if (postProcessSettings.isEmpty() || skySettings.isEmpty() || meshRenderSystem.isEmpty()) {
            return;
        }
        skySettings.get().setSkyIntensity(skyIntensity[0]);
        skySettings.get().setAmbientIntensity(ambientIntensity[0]);
        applyPostProcess(postProcessSettings.get());
        meshRenderSystem.get().setShadowDistance(shadowDistance[0]);
        meshRenderSystem.get().setClusteringEnabled(lightCullingEnabled);
    }

    private void applyPostProcess(PostProcessSettings postProcess) {
        postProcess.setGradeExposure(exposure[0]);
        postProcess.setVignetteStrength(vignette[0]);
        postProcess.setBloomEnabled(bloomEnabled);
        postProcess.setBloom(postProcess.bloomThreshold(), postProcess.bloomKnee(), bloomIntensity[0]);
        postProcess.setAmbientOcclusionEnabled(ambientOcclusionEnabled);
        postProcess.setAmbientOcclusion(ambientOcclusionRadius[0], ambientOcclusionIntensity[0]);
        postProcess.setAntiAliasingEnabled(antiAliasEnabled);
    }
}
