package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.ImGuiShell;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.profiling.FrameProfiler;
import fr.epistudio.epysia.render.backend.DrawStatistics;
import fr.epistudio.epysia.render.mesh.ShadowStatistics;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTableFlags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ProfilerView {

    public static final String WINDOW_TITLE = "Profiler";

    private static final float NANOS_PER_MILLISECOND = 1_000_000.0f;
    private static final int HISTORY_LENGTH = 120;
    private static final float PLOT_HEIGHT = 60.0f;
    private static final float PLOT_HEADROOM = 1.2f;
    private static final float PERCENT_SCALE = 100.0f;
    private static final float MINIMUM_FRAMERATE = 1.0f;
    private static final float DEFAULT_WINDOW_WIDTH = 420.0f;
    private static final float DEFAULT_WINDOW_HEIGHT = 640.0f;
    private static final int TABLE_FLAGS = ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp
            | ImGuiTableFlags.BordersInnerV;

    private final EditorScene3DHost sceneHost;
    private final ImGuiShell shell;
    private final Supplier<SceneDocument> activeDocument;
    private final ViewportView viewportView;
    private final FrameTimeHistory frameHistory = new FrameTimeHistory(HISTORY_LENGTH);
    private final SectionAverages gpuAverages = new SectionAverages(HISTORY_LENGTH);
    private final SectionAverages cpuAverages = new SectionAverages(HISTORY_LENGTH);
    private boolean visible = true;
    private boolean recenterRequested;

    public ProfilerView(EditorScene3DHost sceneHost, ImGuiShell shell,
                        Supplier<SceneDocument> activeDocument, ViewportView viewportView) {
        this.sceneHost = sceneHost;
        this.shell = shell;
        this.activeDocument = activeDocument;
        this.viewportView = viewportView;
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
        applyWindowPlacement();
        if (!ImGui.begin(I18n.label(TextKey.EDITOR_PROFILER_VIEW_TITLE, WINDOW_TITLE))) {
            ImGui.end();
            return;
        }
        clampWhenFullyOffscreen();
        sampleThisFrame();
        renderSections();
        ImGui.end();
    }

    private void applyWindowPlacement() {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowSize(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT, ImGuiCond.FirstUseEver);
        int condition = recenterRequested ? ImGuiCond.Always : ImGuiCond.FirstUseEver;
        ImGui.setNextWindowPos(
                viewport.getCenterX() - DEFAULT_WINDOW_WIDTH * 0.5f,
                viewport.getCenterY() - DEFAULT_WINDOW_HEIGHT * 0.5f,
                condition);
        recenterRequested = false;
    }

    private void clampWhenFullyOffscreen() {
        if (ImGui.isWindowDocked()) {
            return;
        }
        ImGuiViewport viewport = ImGui.getMainViewport();
        recenterRequested = isFullyOutsideWorkArea(viewport,
                ImGui.getWindowPosX(), ImGui.getWindowPosY(),
                ImGui.getWindowSizeX(), ImGui.getWindowSizeY());
    }

    private static boolean isFullyOutsideWorkArea(ImGuiViewport viewport,
                                                  float windowX, float windowY, float width, float height) {
        float left = viewport.getWorkPosX();
        float top = viewport.getWorkPosY();
        float right = left + viewport.getWorkSizeX();
        float bottom = top + viewport.getWorkSizeY();
        return windowX + width <= left || windowX >= right
                || windowY + height <= top || windowY >= bottom;
    }

    private void renderSections() {
        renderFrameSummary();
        ImGui.separator();
        renderToggles();
        ImGui.separator();
        renderGpuTable();
        ImGui.separator();
        renderCpuTable();
        ImGui.separator();
        renderDrawStatistics();
        ImGui.separator();
        renderShadowStatistics();
        ImGui.separator();
        renderSceneStatistics();
    }

    private void sampleThisFrame() {
        frameHistory.record(frameMilliseconds());
        for (Map.Entry<String, Long> entry : gpuTimings().entrySet()) {
            gpuAverages.record(entry.getKey(), entry.getValue() / NANOS_PER_MILLISECOND);
        }
        for (Map.Entry<String, Long> entry : cpuTimings().entrySet()) {
            cpuAverages.record(entry.getKey(), entry.getValue() / NANOS_PER_MILLISECOND);
        }
    }

    private static float frameMilliseconds() {
        return 1000.0f / Math.max(MINIMUM_FRAMERATE, ImGui.getIO().getFramerate());
    }

    private Map<String, Long> gpuTimings() {
        return sceneHost.backend().latestProfileTimingsNanos();
    }

    private Map<String, Long> cpuTimings() {
        return sceneHost.engine().profiler().sections();
    }

    private void renderFrameSummary() {
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_FPS,
                String.format("%.0f", ImGui.getIO().getFramerate())));
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_FRAME_SUMMARY,
                String.format("%.3f", frameHistory.latest()),
                String.format("%.3f", frameHistory.minimum()),
                String.format("%.3f", frameHistory.average()),
                String.format("%.3f", frameHistory.maximum()),
                frameHistory.length()));
        ImGui.plotLines("##frame-history", frameHistory.samples(), frameHistory.length(),
                frameHistory.cursor(), "", 0.0f, frameHistory.maximum() * PLOT_HEADROOM,
                0.0f, PLOT_HEIGHT);
    }

    private void renderToggles() {
        boolean vsync = shell.isVsyncEnabled();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_PROFILER_VIEW_VERTICAL_SYNC,
                "profiler-vsync"), vsync)) {
            shell.setVsyncEnabled(!vsync);
        }
        boolean supersampled = viewportView.supersampleFactor() > 1;
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_PROFILER_VIEW_VIEWPORT_SUPERSAMPLING,
                "profiler-supersampling"), supersampled)) {
            viewportView.setSupersampleFactor(supersampled ? 1 : 2);
        }
        boolean shadowCaching = sceneHost.meshRenderSystem().shadowCachingEnabled();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_PROFILER_VIEW_SHADOW_MAP_CACHING,
                "profiler-shadow-caching"), shadowCaching)) {
            sceneHost.meshRenderSystem().setShadowCachingEnabled(!shadowCaching);
        }
        boolean shadowSplit = sceneHost.meshRenderSystem().shadowSplitEnabled();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_PROFILER_VIEW_SHADOW_SPLIT,
                "profiler-shadow-split"), shadowSplit)) {
            sceneHost.meshRenderSystem().setShadowSplitEnabled(!shadowSplit);
        }
        boolean instancing = sceneHost.meshRenderSystem().instancingEnabled();
        if (ImGui.checkbox(I18n.label(TextKey.EDITOR_PROFILER_VIEW_GPU_INSTANCING,
                "profiler-gpu-instancing"), instancing)) {
            sceneHost.meshRenderSystem().setInstancingEnabled(!instancing);
        }
        if (vsync) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_VSYNC_HELP));
        }
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_RENDERING_SIZE,
                sceneHost.currentWidth(), sceneHost.currentHeight()));
    }

    private void renderGpuTable() {
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_GPU_PASSES));
        Map<String, Long> timings = gpuTimings();
        if (timings.isEmpty()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_NO_SAMPLES));
            return;
        }
        float totalMilliseconds = totalMilliseconds(timings);
        if (!beginTimingTable("##gpu-timings")) {
            return;
        }
        for (Map.Entry<String, Long> entry : sortedByCostDescending(timings)) {
            float milliseconds = entry.getValue() / NANOS_PER_MILLISECOND;
            appendTimingRow(entry.getKey(), milliseconds, gpuAverages.average(entry.getKey()),
                    percentOf(milliseconds, totalMilliseconds));
        }
        ImGui.endTable();
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_GPU_TOTAL,
                String.format("%.3f", totalMilliseconds)));
    }

    private void renderCpuTable() {
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_CPU_SECTIONS));
        Map<String, Long> timings = cpuTimings();
        if (timings.isEmpty()) {
            ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_NO_SAMPLES));
            return;
        }
        float engineMilliseconds = milliseconds(timings, FrameProfiler.RENDER_SECTION)
                + milliseconds(timings, FrameProfiler.TICK_SECTION);
        if (!beginTimingTable("##cpu-timings")) {
            return;
        }
        for (Map.Entry<String, Long> entry : sortedByCostDescending(timings)) {
            float value = entry.getValue() / NANOS_PER_MILLISECOND;
            appendTimingRow(entry.getKey(), value, cpuAverages.average(entry.getKey()),
                    percentOf(value, engineMilliseconds));
        }
        ImGui.endTable();
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_ENGINE_CPU,
                String.format("%.3f", engineMilliseconds)));
        renderEditorShellTimings();
    }

    private void renderEditorShellTimings() {
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_EDITOR_SHELL));
        appendShellTimingRow(TextKey.EDITOR_PROFILER_VIEW_SHELL_POLL, shell.pollNanos());
        appendShellTimingRow(TextKey.EDITOR_PROFILER_VIEW_SHELL_UI_BUILD, shell.uiBuildNanos());
        appendShellTimingRow(TextKey.EDITOR_PROFILER_VIEW_SHELL_UI_DRAW, shell.drawDataNanos());
        appendShellTimingRow(TextKey.EDITOR_PROFILER_VIEW_SHELL_DETACHED_VIEWPORTS, shell.viewportsNanos());
        appendShellTimingRow(TextKey.EDITOR_PROFILER_VIEW_SHELL_PRESENT, shell.swapNanos());
    }

    private static void appendShellTimingRow(TextKey labelKey, long nanos) {
        ImGui.textDisabled(String.format("%s  %.3f ms",
                I18n.translate(labelKey), nanos / NANOS_PER_MILLISECOND));
    }

    private static boolean beginTimingTable(String identifier) {
        if (!ImGui.beginTable(identifier, 4, TABLE_FLAGS)) {
            return false;
        }
        ImGui.tableSetupColumn(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TABLE_SECTION));
        ImGui.tableSetupColumn(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TABLE_MS));
        ImGui.tableSetupColumn(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TABLE_AVG_MS));
        ImGui.tableSetupColumn(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TABLE_PERCENT));
        ImGui.tableHeadersRow();
        return true;
    }

    private static void appendTimingRow(String name, float milliseconds, float averageMilliseconds, float percent) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textUnformatted(name);
        ImGui.tableNextColumn();
        ImGui.text(String.format("%.3f", milliseconds));
        ImGui.tableNextColumn();
        ImGui.text(String.format("%.3f", averageMilliseconds));
        ImGui.tableNextColumn();
        ImGui.text(String.format("%.1f", percent));
    }

    private void renderDrawStatistics() {
        DrawStatistics statistics = sceneHost.backend().drawStatistics();
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DRAW_STATISTICS));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DRAW_CALLS,
                statistics.drawCalls(), statistics.instancedDrawCalls()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_INSTANCE_BATCHES,
                sceneHost.meshRenderSystem().batchCount(),
                sceneHost.meshRenderSystem().instancedBatchCount(), statistics.instances()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TRIANGLES, statistics.triangles()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_RENDER_PASSES, statistics.passes()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_PIPELINE_SWITCHES,
                statistics.pipelineSwitches()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_BINDING_SET_SWITCHES,
                statistics.bindingSetSwitches()));
    }
    private void renderShadowStatistics() {
        ShadowStatistics statistics = sceneHost.meshRenderSystem().shadowStatistics();
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_SHADOWS));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TARGETS_RENDERED,
                statistics.targetsRendered(), statistics.targetsSkipped()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_CASTERS_SUBMITTED,
                statistics.castersSubmitted()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TIME_ANIMATED_CASTERS,
                statistics.animatedCasters()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_STATIC_LAYERS_REBUILT,
                statistics.staticLayersRebuilt()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DYNAMIC_CASTERS_DRAWN,
                statistics.dynamicCastersDrawn()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DEPTH_COPIES,
                statistics.depthCopies()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_CACHED_STATIC_DEPTH,
                String.format("%.1f", sceneHost.meshRenderSystem().shadowStaticVideoMemoryBytes()
                        / (1024.0 * 1024.0))));
    }
    private void renderSceneStatistics() {
        ImGui.text(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_SCENE));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_OBJECTS,
                activeDocument.get().scene().gameObjects().size()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_MESHES_SUBMITTED,
                sceneHost.meshRenderSystem().submittedMeshCount()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_MESHES_CULLED,
                sceneHost.meshRenderSystem().culledMeshCount()));
        ImGui.textDisabled(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_LIGHTS, countLights()));
    }
    private int countLights() {
        int lights = 0;
        for (GameObject gameObject : activeDocument.get().scene().gameObjects()) {
            if (gameObject.getComponent(Light.class).isPresent()) {
                lights++;
            }
        }
        return lights;
    }

    private static float percentOf(float value, float total) {
        return total <= 0.0f ? 0.0f : value / total * PERCENT_SCALE;
    }

    private static float milliseconds(Map<String, Long> timings, String sectionName) {
        return timings.getOrDefault(sectionName, 0L) / NANOS_PER_MILLISECOND;
    }

    private static List<Map.Entry<String, Long>> sortedByCostDescending(Map<String, Long> timings) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(timings.entrySet());
        entries.sort(Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed());
        return entries;
    }

    private static float totalMilliseconds(Map<String, Long> timings) {
        long total = 0L;
        for (long nanos : timings.values()) {
            total += nanos;
        }
        return total / NANOS_PER_MILLISECOND;
    }
}
