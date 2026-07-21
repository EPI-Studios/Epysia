package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.editor.runtime.EditorScene3DHost;
import fr.epistudio.epysia.editor.scene.SceneDocument;
import fr.epistudio.epysia.editor.shell.ImGuiShell;
import fr.epistudio.epysia.gameobjects.GameObject;
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
        if (!ImGui.begin(WINDOW_TITLE)) {
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
        ImGui.text(String.format("%.0f fps", ImGui.getIO().getFramerate()));
        ImGui.text(String.format("Frame %.3f ms  (min %.3f  avg %.3f  max %.3f over %d frames)",
                frameHistory.latest(), frameHistory.minimum(), frameHistory.average(),
                frameHistory.maximum(), frameHistory.length()));
        ImGui.plotLines("##frame-history", frameHistory.samples(), frameHistory.length(),
                frameHistory.cursor(), "", 0.0f, frameHistory.maximum() * PLOT_HEADROOM,
                0.0f, PLOT_HEIGHT);
    }

    private void renderToggles() {
        boolean vsync = shell.isVsyncEnabled();
        if (ImGui.checkbox("Vertical sync", vsync)) {
            shell.setVsyncEnabled(!vsync);
        }
        boolean supersampled = viewportView.supersampleFactor() > 1;
        if (ImGui.checkbox("Viewport supersampling (2x)", supersampled)) {
            viewportView.setSupersampleFactor(supersampled ? 1 : 2);
        }
        boolean shadowCaching = sceneHost.meshRenderSystem().shadowCachingEnabled();
        if (ImGui.checkbox("Shadow map caching", shadowCaching)) {
            sceneHost.meshRenderSystem().setShadowCachingEnabled(!shadowCaching);
        }
        boolean shadowSplit = sceneHost.meshRenderSystem().shadowSplitEnabled();
        if (ImGui.checkbox("Static/dynamic shadow caster split", shadowSplit)) {
            sceneHost.meshRenderSystem().setShadowSplitEnabled(!shadowSplit);
        }
        boolean instancing = sceneHost.meshRenderSystem().instancingEnabled();
        if (ImGui.checkbox("Automatic GPU instancing", instancing)) {
            sceneHost.meshRenderSystem().setInstancingEnabled(!instancing);
        }
        if (vsync) {
            ImGui.textDisabled("Frame rate is capped to the monitor refresh rate. Disable to measure.");
        }
        ImGui.textDisabled(String.format("Rendering %d x %d",
                sceneHost.currentWidth(), sceneHost.currentHeight()));
    }

    private void renderGpuTable() {
        ImGui.text("GPU passes");
        Map<String, Long> timings = gpuTimings();
        if (timings.isEmpty()) {
            ImGui.textDisabled("No samples yet.");
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
        ImGui.text(String.format("GPU total %.3f ms", totalMilliseconds));
    }

    private void renderCpuTable() {
        ImGui.text("CPU sections");
        Map<String, Long> timings = cpuTimings();
        if (timings.isEmpty()) {
            ImGui.textDisabled("No samples yet.");
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
        ImGui.text(String.format("Engine CPU %.3f ms", engineMilliseconds));
        renderEditorShellTimings();
    }

    private void renderEditorShellTimings() {
        ImGui.text("Editor shell (CPU, main thread)");
        appendShellTimingRow("poll and new frame", shell.pollNanos());
        appendShellTimingRow("ui build", shell.uiBuildNanos());
        appendShellTimingRow("ui draw", shell.drawDataNanos());
        appendShellTimingRow("detached viewports", shell.viewportsNanos());
        appendShellTimingRow("present (includes vsync wait)", shell.swapNanos());
    }

    private static void appendShellTimingRow(String label, long nanos) {
        ImGui.textDisabled(String.format("%s  %.3f ms", label, nanos / NANOS_PER_MILLISECOND));
    }

    private static boolean beginTimingTable(String identifier) {
        if (!ImGui.beginTable(identifier, 4, TABLE_FLAGS)) {
            return false;
        }
        ImGui.tableSetupColumn("Section");
        ImGui.tableSetupColumn("ms");
        ImGui.tableSetupColumn("avg ms");
        ImGui.tableSetupColumn("%");
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
        ImGui.text("Draw statistics");
        ImGui.textDisabled("Draw calls: " + statistics.drawCalls()
                + " (instanced " + statistics.instancedDrawCalls() + ")");
        ImGui.textDisabled("Instance batches: " + sceneHost.meshRenderSystem().batchCount()
                + " — collapsed: " + sceneHost.meshRenderSystem().instancedBatchCount()
                + " — instances drawn: " + statistics.instances());
        ImGui.textDisabled("Triangles: " + statistics.triangles());
        ImGui.textDisabled("Render passes: " + statistics.passes());
        ImGui.textDisabled("Pipeline switches: " + statistics.pipelineSwitches());
        ImGui.textDisabled("Binding set switches: " + statistics.bindingSetSwitches());
    }

    private void renderShadowStatistics() {
        ShadowStatistics statistics = sceneHost.meshRenderSystem().shadowStatistics();
        ImGui.text("Shadows");
        ImGui.textDisabled("Targets rendered: " + statistics.targetsRendered()
                + " — skipped: " + statistics.targetsSkipped());
        ImGui.textDisabled("Casters submitted: " + statistics.castersSubmitted());
        ImGui.textDisabled("Time animated casters: " + statistics.animatedCasters());
        ImGui.textDisabled("Static layers rebuilt: " + statistics.staticLayersRebuilt());
        ImGui.textDisabled("Dynamic casters drawn: " + statistics.dynamicCastersDrawn());
        ImGui.textDisabled("Depth copies: " + statistics.depthCopies());
        ImGui.textDisabled(String.format("Cached static depth: %.1f MiB",
                sceneHost.meshRenderSystem().shadowStaticVideoMemoryBytes() / (1024.0 * 1024.0)));
    }

    private void renderSceneStatistics() {
        ImGui.text("Scene");
        ImGui.textDisabled("Objects: " + activeDocument.get().scene().gameObjects().size());
        ImGui.textDisabled("Meshes submitted: " + sceneHost.meshRenderSystem().submittedMeshCount());
        ImGui.textDisabled("Meshes culled: " + sceneHost.meshRenderSystem().culledMeshCount());
        ImGui.textDisabled("Lights: " + countLights());
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
