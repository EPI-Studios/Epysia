package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.preferences.ProfilerPreferences;

import java.io.IOException;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.ImGuiShell;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.profiling.FrameProfiler;
import fr.epistudio.epysia.render.backend.DrawStatistics;
import fr.epistudio.epysia.render.mesh.ShadowStatistics;
import fr.epistudio.epysia.editor.ui.kit.Texts;
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
    private static final String FRAME_TOTAL_SECTION = "frameTotal";
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
    private final PanelTimings panelTimings;
    private final FrameTimeHistory frameHistory = new FrameTimeHistory(HISTORY_LENGTH);
    private final AllocationMeter allocationMeter = new AllocationMeter();
    private final SectionAverages gpuAverages = new SectionAverages(HISTORY_LENGTH);
    private final SectionAverages cpuAverages = new SectionAverages(HISTORY_LENGTH);
    private boolean visible = true;
    private boolean preferencesApplied;
    private boolean recenterRequested;

    public ProfilerView(EditorScene3DHost sceneHost, ImGuiShell shell,
                        Supplier<SceneDocument> activeDocument, ViewportView viewportView,
                        PanelTimings panelTimings) {
        this.sceneHost = sceneHost;
        this.shell = shell;
        this.activeDocument = activeDocument;
        this.viewportView = viewportView;
        this.panelTimings = panelTimings;
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
        if (!ImGui.begin(I18n.label(TextKey.EDITOR_PROFILER_VIEW_WINDOW_TITLE, WINDOW_TITLE))) {
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
        ImGui.setNextWindowSize(EditorScale.of(DEFAULT_WINDOW_WIDTH), EditorScale.of(DEFAULT_WINDOW_HEIGHT), ImGuiCond.FirstUseEver);
        int condition = recenterRequested ? ImGuiCond.Always : ImGuiCond.FirstUseEver;
        ImGui.setNextWindowPos(
                viewport.getCenterX() - EditorScale.of(DEFAULT_WINDOW_WIDTH) * 0.5f,
                viewport.getCenterY() - EditorScale.of(DEFAULT_WINDOW_HEIGHT) * 0.5f,
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
        appendPanelBreakdown();
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
        allocationMeter.sample();
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
        ImGui.textUnformatted(String.format("%.0f fps", ImGui.getIO().getFramerate()));
        ImGui.textUnformatted(String.format("Frame %.3f ms  (min %.3f  avg %.3f  max %.3f over %d frames)",
                frameHistory.latest(), frameHistory.minimum(), frameHistory.average(),
                frameHistory.maximum(), frameHistory.length()));
        ImGui.plotLines("##frame-history", frameHistory.samples(), frameHistory.length(),
                frameHistory.cursor(), "", 0.0f, frameHistory.maximum() * PLOT_HEADROOM,
                0.0f, EditorScale.of(PLOT_HEIGHT));
        renderAllocationSummary();
    }

    private void renderAllocationSummary() {
        if (!allocationMeter.available()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_ALLOCATION_UNAVAILABLE));
            return;
        }
        ImGui.textUnformatted(String.format("Garbage %.1f KB/frame  (%.1f MB/s)",
                allocationMeter.bytesPerFrame() / 1024.0, allocationMeter.megabytesPerSecond()));
        Texts.muted(String.format("GC since launch: %d collections, %d ms total",
                allocationMeter.collectionsSinceStart(), allocationMeter.collectionMillisSinceStart()));
        renderShaderCompileSummary();
    }

    private void renderShaderCompileSummary() {
        Texts.muted(String.format(
                "Shader compile: %d programs, %.0f ms total, worst frame %.1f ms, worst program %.1f ms%s",
                sceneHost.backend().shaderCompileCount(),
                sceneHost.backend().shaderCompileNanos() / NANOS_PER_MILLISECOND,
                sceneHost.backend().worstFrameShaderCompileNanos() / NANOS_PER_MILLISECOND,
                sceneHost.backend().worstProgramShaderCompileNanos() / NANOS_PER_MILLISECOND,
                sceneHost.backend().frameShaderCompileNanos() > 0L ? "  <- compiling now" : ""));
    }

    private void applyStoredPreferencesOnce() {
        if (preferencesApplied) {
            return;
        }
        preferencesApplied = true;
        ProfilerPreferences stored = ProfilerPreferences.load(ProfilerPreferences.defaultFile());
        shell.setVsyncEnabled(stored.verticalSync());
        shell.setFrameRateCapEnabled(stored.frameRateCapEnabled());
        viewportView.setSupersampleFactor(stored.viewportSupersampling() ? 2 : 1);
        sceneHost.meshRenderSystem().setShadowCachingEnabled(stored.shadowCaching());
        sceneHost.meshRenderSystem().setShadowSplitEnabled(stored.shadowSplit());
        sceneHost.meshRenderSystem().setDepthPrepassEnabled(stored.depthPrepass());
        sceneHost.meshRenderSystem().setInstancingEnabled(stored.instancing());
    }

    private void persistPreferences() {
        ProfilerPreferences current = new ProfilerPreferences(shell.isVsyncEnabled(),
                shell.isFrameRateCapEnabled(), viewportView.supersampleFactor() > 1,
                sceneHost.meshRenderSystem().shadowCachingEnabled(),
                sceneHost.meshRenderSystem().shadowSplitEnabled(),
                sceneHost.meshRenderSystem().depthPrepassEnabled(),
                sceneHost.meshRenderSystem().instancingEnabled());
        try {
            current.save(ProfilerPreferences.defaultFile());
        } catch (IOException ignored) {
            preferencesApplied = true;
        }
    }

    private void renderToggles() {
        applyStoredPreferencesOnce();
        boolean vsync = shell.isVsyncEnabled();
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_VERTICAL_SYNC), vsync)) {
            shell.setVsyncEnabled(!vsync);
            persistPreferences();
        }
        boolean frameCap = shell.isFrameRateCapEnabled();
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_FRAME_RATE_CAP,
                shell.frameRateCap()), frameCap)) {
            shell.setFrameRateCapEnabled(!frameCap);
            persistPreferences();
        }
        boolean supersampled = viewportView.supersampleFactor() > 1;
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_VIEWPORT_SUPERSAMPLING), supersampled)) {
            viewportView.setSupersampleFactor(supersampled ? 1 : 2);
            persistPreferences();
        }
        boolean shadowCaching = sceneHost.meshRenderSystem().shadowCachingEnabled();
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_SHADOW_MAP_CACHING), shadowCaching)) {
            sceneHost.meshRenderSystem().setShadowCachingEnabled(!shadowCaching);
            persistPreferences();
        }
        boolean shadowSplit = sceneHost.meshRenderSystem().shadowSplitEnabled();
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_SHADOW_SPLIT), shadowSplit)) {
            sceneHost.meshRenderSystem().setShadowSplitEnabled(!shadowSplit);
            persistPreferences();
        }
        boolean prepass = sceneHost.meshRenderSystem().depthPrepassEnabled();
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DEPTH_PREPASS), prepass)) {
            sceneHost.meshRenderSystem().setDepthPrepassEnabled(!prepass);
            persistPreferences();
        }
        if (prepass) {
            Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DEPTH_PREPASS_HINT));
        }
        boolean instancing = sceneHost.meshRenderSystem().instancingEnabled();
        if (ImGui.checkbox(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_GPU_INSTANCING), instancing)) {
            sceneHost.meshRenderSystem().setInstancingEnabled(!instancing);
            persistPreferences();
        }
        if (vsync) {
            Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_VSYNC_HELP));
        }
        if (!frameCap && !vsync) {
            Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_UNCAPPED_HINT));
        }
        Texts.muted(String.format("Rendering %d x %d",
                sceneHost.currentWidth(), sceneHost.currentHeight()));
    }

    private void renderGpuTable() {
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_GPU_PASSES));
        Map<String, Long> timings = gpuTimings();
        if (timings.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_NO_SAMPLES));
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
        ImGui.textUnformatted(String.format("GPU sections %.3f ms  (frame %.3f ms measured end to end)",
                totalMilliseconds, frameTotalMilliseconds(timings)));
    }

    private void renderCpuTable() {
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_CPU_SECTIONS));
        Map<String, Long> timings = cpuTimings();
        if (timings.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_NO_SAMPLES));
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
        ImGui.textUnformatted(String.format("Engine CPU %.3f ms", engineMilliseconds));
        renderEditorShellTimings();
    }

    private void renderEditorShellTimings() {
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_EDITOR_SHELL));
        appendShellTimingRow("poll and new frame", shell.pollNanos());
        appendShellTimingRow("ui build", shell.uiBuildNanos());
        appendShellTimingRow("ui draw", shell.drawDataNanos());
        appendShellTimingRow("detached viewports", shell.viewportsNanos());
        appendShellTimingRow("present (includes vsync wait)", shell.swapNanos());
    }

    private void appendPanelBreakdown() {
        List<PanelTimings.Entry> entries = panelTimings.ordered();
        if (entries.isEmpty()) {
            return;
        }
        ImGui.spacing();
        ImGui.textUnformatted(String.format("ui build by panel  (total %.3f ms)", panelTimings.totalMilliseconds()));
        for (PanelTimings.Entry entry : entries) {
            Texts.muted(String.format("%s  %.3f ms", entry.name(), entry.milliseconds()));
        }
        appendViewportBreakdown();
    }

    private void appendSceneImageBreakdown() {
        long[] steps = sceneHost.frameStepNanos();
        String[] labels = {"target resize", "gl state capture", "engine.render", "gl state restore",
                "probe refresh"};
        ImGui.spacing();
        ImGui.textUnformatted(String.format("scene image by step   (watchers: %d, frames skipped: %d)",
                sceneHost.shaderWatcherListenerCount(), sceneHost.skippedFrames()));
        for (int index = 0; index < labels.length; index++) {
            Texts.muted(String.format("  %s  %.3f ms",
                    labels[index], steps[index] / NANOS_PER_MILLISECOND));
        }
    }

    private void appendViewportBreakdown() {
        List<PanelTimings.Entry> entries = viewportView.timings().ordered();
        if (entries.isEmpty()) {
            return;
        }
        ImGui.spacing();
        ImGui.textUnformatted(String.format("viewport by step  (measured %.3f ms)",
                viewportView.timings().totalMilliseconds()));
        for (PanelTimings.Entry entry : entries) {
            Texts.muted(String.format("  %s  %.3f ms", entry.name(), entry.milliseconds()));
        }
        appendSceneImageBreakdown();
    }

    private static void appendShellTimingRow(String label, long nanos) {
        Texts.muted(String.format("%s  %.3f ms", label, nanos / NANOS_PER_MILLISECOND));
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
        ImGui.textUnformatted(String.format("%.3f", milliseconds));
        ImGui.tableNextColumn();
        ImGui.textUnformatted(String.format("%.3f", averageMilliseconds));
        ImGui.tableNextColumn();
        ImGui.textUnformatted(String.format("%.1f", percent));
    }

    private void renderDrawStatistics() {
        DrawStatistics statistics = sceneHost.backend().drawStatistics();
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DRAW_STATISTICS));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DRAW_CALLS,
                statistics.drawCalls(), statistics.instancedDrawCalls()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_INSTANCE_BATCHES,
                sceneHost.meshRenderSystem().batchCount(),
                sceneHost.meshRenderSystem().instancedBatchCount(), statistics.instances()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TRIANGLES, statistics.triangles()));
        renderRenderResolutionRow();
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_RENDER_PASSES, statistics.passes()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_PIPELINE_SWITCHES, statistics.pipelineSwitches()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_BINDING_SWITCHES, statistics.bindingSetSwitches()));
    }

    private void renderRenderResolutionRow() {
        int width = viewportView.renderedWidth();
        int height = viewportView.renderedHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Texts.muted(String.format("Shaded pixels: %d x %d = %.1f Mpx (supersampling %dx)",
                width, height, width * (double) height / 1_000_000.0, viewportView.supersampleFactor()));
    }

    private void renderShadowStatistics() {
        ShadowStatistics statistics = sceneHost.meshRenderSystem().shadowStatistics();
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_SHADOWS));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_TARGETS_RENDERED, statistics.targetsRendered()
                + ", skipped: " + statistics.targetsSkipped()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_CASTERS_SUBMITTED, statistics.castersSubmitted()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_ANIMATED_CASTERS, statistics.animatedCasters()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_STATIC_LAYERS, statistics.staticLayersRebuilt()
                + ", scrolled: " + statistics.staticLayersScrolled()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_SCROLLED_CASTERS, statistics.scrolledCastersDrawn()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DYNAMIC_CASTERS, statistics.dynamicCastersDrawn()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_DEPTH_COPIES, statistics.depthCopies()));
        Texts.muted(String.format("Cached static depth: %.1f MiB",
                sceneHost.meshRenderSystem().shadowStaticVideoMemoryBytes() / (1024.0 * 1024.0)));
    }

    private void renderSceneStatistics() {
        ImGui.textUnformatted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_SCENE));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_OBJECTS, activeDocument.get().scene().gameObjects().size()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_MESHES_SUBMITTED, sceneHost.meshRenderSystem().submittedMeshCount()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_BOUNDS_CACHE, sceneHost.meshRenderSystem().boundsCacheHits()
                + " hits, " + sceneHost.meshRenderSystem().boundsCacheMisses() + " recomputed"));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_MESHES_CULLED, sceneHost.meshRenderSystem().culledMeshCount()));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_ANIMATION, sceneHost.meshRenderSystem().animationsCulledThisFrame()
                + " culled, " + sceneHost.meshRenderSystem().animationsCadencedThisFrame() + " cadenced, "
                + sceneHost.meshRenderSystem().skinnedDeformationsThisFrame() + " skinned"));
        Texts.muted(I18n.translate(TextKey.EDITOR_PROFILER_VIEW_LIGHTS, countLights()));
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
        for (Map.Entry<String, Long> entry : timings.entrySet()) {
            if (!FRAME_TOTAL_SECTION.equals(entry.getKey())) {
                total += entry.getValue();
            }
        }
        return total / NANOS_PER_MILLISECOND;
    }

    private static float frameTotalMilliseconds(Map<String, Long> timings) {
        return timings.getOrDefault(FRAME_TOTAL_SECTION, 0L) / NANOS_PER_MILLISECOND;
    }
}
