package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.LightProbeVolume;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.render.baking.BakeProgress;
import fr.epistudio.epysia.render.baking.BakeRequest;
import fr.epistudio.epysia.render.baking.LightBakeHashes;
import fr.epistudio.epysia.render.baking.LightBakeOutput;
import fr.epistudio.epysia.render.baking.LightBaker;
import fr.epistudio.epysia.render.baking.LightBakerRegistry;
import fr.epistudio.epysia.render.baking.LightmapBaker;
import fr.epistudio.epysia.render.baking.ProbeBaker;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;
import imgui.ImGui;
import imgui.flag.ImGuiCond;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

public final class LightingView {

    public static final String WINDOW_TITLE = "Lighting";

    private static final String PROBES_DIRECTORY_NAME = "probes";
    private static final int PROBES_PER_FRAME = 2;
    private static final float DEFAULT_WINDOW_WIDTH = 320.0f;
    private static final float DEFAULT_WINDOW_HEIGHT = 180.0f;

    private final EditorScene3DHost sceneHost;
    private final Supplier<SceneDocument> activeDocument;
    private final Path outputDirectory;
    private final LightBakerRegistry bakers = new LightBakerRegistry();
    private Optional<LightBaker> runningBaker = Optional.empty();
    private BakeProgress lastProgress = BakeProgress.idle();
    private long checkedModificationCount = -1L;
    private long currentSceneHash;
    private boolean visible;

    public LightingView(EditorScene3DHost sceneHost, Supplier<SceneDocument> activeDocument, Path projectRoot) {
        this.sceneHost = sceneHost;
        this.activeDocument = activeDocument;
        this.outputDirectory = projectRoot.resolve(PROBES_DIRECTORY_NAME);
        bakers.register(new ProbeBaker());
        bakers.register(new LightmapBaker());
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean value) {
        visible = value;
    }

    public void render() {
        if (!visible) {
            return;
        }
        ImGui.setNextWindowSize(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, ImGuiCond.FirstUseEver);
        if (!ImGui.begin(I18n.label(TextKey.EDITOR_LIGHTING_VIEW_TITLE, WINDOW_TITLE))) {
            ImGui.end();
            return;
        }
        renderContents(activeDocument.get().scene());
        ImGui.end();
    }

    private void renderContents(Scene scene) {
        Optional<LightProbeVolume> volume = findVolume(scene);
        if (volume.isEmpty()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_LIGHTING_VIEW_NO_VOLUME));
            stepRunningBake();
            return;
        }
        renderVolumeStatus(scene, volume.get());
        renderBakeControls();
        stepRunningBake();
    }

    private void renderVolumeStatus(Scene scene, LightProbeVolume volume) {
        int probeCount = volume.resolutionX() * volume.resolutionY() * volume.resolutionZ();
        ImGui.text(I18n.translate(TextKey.EDITOR_LIGHTING_VIEW_PROBES, probeCount));
        refreshSceneHash(scene);
        if (volume.bakedProbes().isEmpty()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_LIGHTING_VIEW_NOT_BAKED));
        } else if (volume.bakedProbes().get().bakeHash() != currentSceneHash) {
            ImGui.textColored(1.0f, 0.72f, 0.25f, 1.0f,
                    I18n.translate(TextKey.EDITOR_LIGHTING_VIEW_OUT_OF_DATE));
        } else {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_LIGHTING_VIEW_UP_TO_DATE));
        }
    }

    private void refreshSceneHash(Scene scene) {
        if (scene.modificationCount() == checkedModificationCount) {
            return;
        }
        checkedModificationCount = scene.modificationCount();
        currentSceneHash = LightBakeHashes.hashScene(scene);
    }

    private void renderBakeControls() {
        if (runningBaker.isPresent()) {
            ImGui.text(I18n.translate(TextKey.EDITOR_LIGHTING_VIEW_BAKING,
                    lastProgress.completedSteps(), lastProgress.totalSteps()));
            if (ImGui.button(I18n.label(TextKey.EDITOR_LIGHTING_VIEW_CANCEL, "lighting-cancel"))) {
                runningBaker.get().cancel();
                runningBaker = Optional.empty();
            }
            return;
        }
        if (ImGui.button(I18n.label(TextKey.EDITOR_LIGHTING_VIEW_BAKE, "lighting-bake"))) {
            startBake(LightBakeOutput.PROBES);
        }
        ImGui.sameLine();
        if (ImGui.button("Bake lightmaps##lighting-bake-lightmap")) {
            startBake(LightBakeOutput.LIGHTMAP);
        }
    }

    private void startBake(LightBakeOutput output) {
        Optional<LightBaker> baker = bakers.firstProducing(output);
        if (baker.isEmpty()) {
            return;
        }
        PostProcessSystem postProcess = sceneHost.engine().renderSystem(PostProcessSystem.class);
        baker.get().start(new BakeRequest(sceneHost.engine(), outputDirectory,
                postProcess::rebindStageTargets));
        runningBaker = baker;
        lastProgress = BakeProgress.running(0, 0);
    }

    private void stepRunningBake() {
        if (runningBaker.isEmpty()) {
            return;
        }
        LightBaker baker = runningBaker.get();
        for (int slice = 0; slice < PROBES_PER_FRAME; slice++) {
            lastProgress = baker.step();
            if (lastProgress.finished()) {
                runningBaker = Optional.empty();
                checkedModificationCount = -1L;
                return;
            }
        }
    }

    private static Optional<LightProbeVolume> findVolume(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            LightProbeVolume volume = gameObject.getComponentOrNull(LightProbeVolume.class);
            if (volume != null) {
                return Optional.of(volume);
            }
        }
        return Optional.empty();
    }
}
