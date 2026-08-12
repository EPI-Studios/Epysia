package fr.epistudio.epysia.editor.ui.kit;

import fr.epistudio.epysia.editor.shell.EditorMotion;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.internal.ImRect;
import imgui.type.ImFloat;

public final class Sliders {

    private static final float TRACK_THICKNESS = 4.0f;
    private static final float GRAB_RADIUS = 6.0f;
    private static final float TICK_HEIGHT = 3.0f;
    private static final float VALUE_GAP = 8.0f;
    private static final String VALUE_FORMAT = "%.3f";

    private Sliders() {
    }

    public static boolean filled(String id, ImFloat value, float minimum, float maximum, float width) {
        return filled(id, value.getData(), minimum, maximum, width, 0);
    }

    public static boolean filled(String id, float[] value, float minimum, float maximum, float width) {
        return filled(id, value, minimum, maximum, width, 0);
    }

    /**
     * A slider whose track is filled up to the grab, with optional tick marks.
     * The interaction is Dear ImGui's own, only the painting is ours.
     */
    public static boolean filled(String id, float[] value, float minimum, float maximum,
                                 float width, int tickCount) {
        float height = ImGui.getFrameHeight();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImRect bounds = new ImRect(left, top, left + width, top + height);
        ImRect grab = new ImRect();
        int id32 = ImGui.getID(id);
        imgui.internal.ImGui.itemSize(bounds, 0.0f);
        if (!imgui.internal.ImGui.itemAdd(bounds, id32)) {
            return false;
        }
        boolean changed = imgui.internal.ImGui.sliderBehavior(bounds, id32, value,
                minimum, maximum, VALUE_FORMAT, 0, grab);
        paint(id, bounds, grab, tickCount);
        renderValue(bounds, value);
        return changed;
    }

    private static void paint(String id, ImRect bounds, ImRect grab, int tickCount) {
        float centerY = (bounds.min.y + bounds.max.y) * 0.5f;
        float half = EditorScale.of(TRACK_THICKNESS) * 0.5f;
        float grabCenterX = (grab.min.x + grab.max.x) * 0.5f;
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(bounds.min.x, centerY - half, bounds.max.x, centerY + half,
                EditorStyle.COLOR_SUNKEN_BACKGROUND, half);
        drawTicks(drawList, bounds, centerY, tickCount);
        drawList.addRectFilled(bounds.min.x, centerY - half, grabCenterX, centerY + half,
                EditorStyle.COLOR_ACCENT, half);
        float emphasis = EditorMotion.towards(id + "-grab",
                ImGui.isItemActive() || ImGui.isItemHovered());
        int grabColor = EditorMotion.blend(EditorStyle.COLOR_TEXT, EditorStyle.COLOR_ACCENT_HOVER, emphasis);
        drawList.addCircleFilled(grabCenterX, centerY, EditorScale.of(GRAB_RADIUS), grabColor);
    }

    private static void drawTicks(ImDrawList drawList, ImRect bounds, float centerY, int tickCount) {
        if (tickCount <= 1) {
            return;
        }
        float span = bounds.max.x - bounds.min.x;
        float height = EditorScale.of(TICK_HEIGHT);
        for (int index = 0; index < tickCount; index++) {
            float x = bounds.min.x + span * index / (tickCount - 1);
            drawList.addLine(x, centerY + height, x, centerY + height * 2.0f, EditorStyle.COLOR_OUTLINE);
        }
    }

    private static void renderValue(ImRect bounds, float[] value) {
        String text = String.format(VALUE_FORMAT, value[0]);
        ImGui.getWindowDrawList().addText(bounds.max.x + EditorScale.of(VALUE_GAP),
                bounds.min.y + ImGui.getStyle().getFramePaddingY(), EditorStyle.COLOR_TEXT_MUTED, text);
    }
}
