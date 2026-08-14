package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.profiling.ProfileCsv;
import fr.epistudio.epysia.profiling.ProfileFrame;
import fr.epistudio.epysia.profiling.ProfileNode;
import fr.epistudio.epysia.profiling.ProfileSpan;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class CpuProfileSection {

    private static final float NANOS_PER_MILLISECOND = 1_000_000.0f;
    private static final float PERCENT_SCALE = 100.0f;
    private static final float TIMELINE_ROW_HEIGHT = 15.0f;
    private static final float TIMELINE_LABEL_MINIMUM_WIDTH = 34.0f;
    private static final float TIMELINE_LABEL_INSET = 3.0f;
    private static final float TIMELINE_ROW_GAP = 1.0f;
    private static final int TIMELINE_BACKGROUND = 0x40000000;
    private static final int TIMELINE_LABEL = 0xFF101010;
    private static final int COLOR_ALPHA = 0xFF000000;
    private static final int COLOR_CHANNEL_FLOOR = 90;
    private static final int COLOR_CHANNEL_RANGE = 130;
    private static final int TABLE_FLAGS = ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp
            | ImGuiTableFlags.BordersInnerV;
    private static final int PARENT_FLAGS = ImGuiTreeNodeFlags.DefaultOpen
            | ImGuiTreeNodeFlags.SpanFullWidth;
    private static final int LEAF_FLAGS = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen
            | ImGuiTreeNodeFlags.SpanFullWidth;
    private static final String EPYSIA_DIRECTORY_NAME = ".epysia";
    private static final String PROFILES_DIRECTORY_NAME = "profiles";

    private final SectionAverages averages;
    private String exportMessage = "";

    CpuProfileSection(SectionAverages averages) {
        this.averages = averages;
    }

    void sample(ProfileFrame frame) {
        sampleAll(frame.roots(), "");
    }

    void render(ProfileFrame frame) {
        float totalMilliseconds = frame.totalNanos() / NANOS_PER_MILLISECOND;
        if (ImGui.beginTable("##cpu-tree", 5, TABLE_FLAGS)) {
            ImGui.tableSetupColumn("Section");
            ImGui.tableSetupColumn("ms");
            ImGui.tableSetupColumn("self");
            ImGui.tableSetupColumn("calls");
            ImGui.tableSetupColumn("%");
            ImGui.tableHeadersRow();
            appendAll(frame.roots(), "", totalMilliseconds);
            ImGui.endTable();
        }
        renderTimeline(frame);
        renderExportButton(frame);
    }

    private void sampleAll(List<ProfileNode> nodes, String parentPath) {
        for (ProfileNode node : nodes) {
            String path = pathOf(parentPath, node.name());
            averages.record(path, node.totalNanos() / NANOS_PER_MILLISECOND);
            sampleAll(node.children(), path);
        }
    }

    private void appendAll(List<ProfileNode> nodes, String parentPath, float totalMilliseconds) {
        for (ProfileNode node : nodes) {
            String path = pathOf(parentPath, node.name());
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            boolean open = ImGui.treeNodeEx(path, node.isLeaf() ? LEAF_FLAGS : PARENT_FLAGS, node.name());
            appendMeasurements(node, path, totalMilliseconds);
            if (open && !node.isLeaf()) {
                appendAll(node.children(), path, totalMilliseconds);
                ImGui.treePop();
            }
        }
    }

    private void appendMeasurements(ProfileNode node, String path, float totalMilliseconds) {
        float milliseconds = node.totalNanos() / NANOS_PER_MILLISECOND;
        ImGui.tableNextColumn();
        ImGui.textUnformatted(String.format("%.3f", milliseconds));
        ImGui.tableNextColumn();
        ImGui.textUnformatted(String.format("%.3f", node.selfNanos() / NANOS_PER_MILLISECOND));
        ImGui.tableNextColumn();
        ImGui.textUnformatted(Integer.toString(node.calls()));
        ImGui.tableNextColumn();
        ImGui.textUnformatted(String.format("%.1f  (avg %.3f)",
                totalMilliseconds <= 0.0f ? 0.0f : milliseconds / totalMilliseconds * PERCENT_SCALE,
                averages.average(path)));
    }

    private void renderTimeline(ProfileFrame frame) {
        List<ProfileSpan> spans = frame.spans();
        if (spans.isEmpty() || frame.spanNanos() <= 0L) {
            return;
        }
        Texts.muted(String.format("Timeline over %.3f ms%s", frame.spanNanos() / NANOS_PER_MILLISECOND,
                frame.droppedSpans() > 0 ? ", " + frame.droppedSpans() + " spans dropped" : ""));
        float width = ImGui.getContentRegionAvailX();
        float rowHeight = EditorScale.of(TIMELINE_ROW_HEIGHT);
        float height = rowHeight * (deepestRow(spans) + 1);
        ImVec2 origin = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(origin.x, origin.y, origin.x + width, origin.y + height, TIMELINE_BACKGROUND);
        for (ProfileSpan span : spans) {
            drawSpan(drawList, span, frame, origin, width, rowHeight);
        }
        ImGui.dummy(width, height);
    }

    private static void drawSpan(ImDrawList drawList, ProfileSpan span, ProfileFrame frame,
                                 ImVec2 origin, float width, float rowHeight) {
        float scale = width / frame.spanNanos();
        float left = origin.x + (span.startNanos() - frame.startNanos()) * scale;
        float right = Math.max(left + 1.0f, origin.x + (span.endNanos() - frame.startNanos()) * scale);
        float top = origin.y + span.depth() * rowHeight;
        drawList.addRectFilled(left, top, right, top + rowHeight - TIMELINE_ROW_GAP, colorOf(span.name()));
        if (right - left >= EditorScale.of(TIMELINE_LABEL_MINIMUM_WIDTH)) {
            drawList.pushClipRect(left, top, right, top + rowHeight, true);
            drawList.addText(left + TIMELINE_LABEL_INSET, top, TIMELINE_LABEL, span.name());
            drawList.popClipRect();
        }
    }

    private static int deepestRow(List<ProfileSpan> spans) {
        int deepest = 0;
        for (ProfileSpan span : spans) {
            deepest = Math.max(deepest, span.depth());
        }
        return deepest;
    }

    private static int colorOf(String name) {
        int hash = name.hashCode();
        int red = channel(hash);
        int green = channel(hash >> 8);
        int blue = channel(hash >> 16);
        return COLOR_ALPHA | blue << 16 | green << 8 | red;
    }

    private static int channel(int bits) {
        return COLOR_CHANNEL_FLOOR + Math.floorMod(bits, COLOR_CHANNEL_RANGE);
    }

    private void renderExportButton(ProfileFrame frame) {
        if (ImGui.button("Export this frame as CSV")) {
            exportMessage = export(frame);
        }
        if (!exportMessage.isEmpty()) {
            Texts.muted(exportMessage);
        }
    }

    private static String export(ProfileFrame frame) {
        Path file = Path.of(System.getProperty("user.home"), EPYSIA_DIRECTORY_NAME,
                PROFILES_DIRECTORY_NAME, "frame-" + System.currentTimeMillis() + ".csv");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, ProfileCsv.of(frame));
            return "Written to " + file;
        } catch (IOException failure) {
            return "Could not write " + file + ": " + failure.getMessage();
        }
    }

    private static String pathOf(String parentPath, String name) {
        return parentPath.isEmpty() ? name : parentPath + "/" + name;
    }
}
