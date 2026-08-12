package fr.epistudio.epysia.editor.ui.kit;

import fr.epistudio.epysia.editor.shell.EditorMotion;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;

public final class SplitButton {

    private static final float PADDING_X = 10.0f;
    private static final float ARROW_WIDTH = 18.0f;
    private static final float ARROW_HALF_WIDTH = 3.5f;
    private static final float ARROW_HEIGHT = 3.0f;
    private static final float ROUNDING = 3.0f;
    private static final float DIVIDER_INSET = 4.0f;

    public record Result(boolean primaryClicked, boolean menuRequested) {
    }

    private SplitButton() {
    }

    public static Result render(String id, String label) {
        float height = ImGui.getFrameHeight();
        float arrowWidth = EditorScale.of(ARROW_WIDTH);
        float primaryWidth = ImGui.calcTextSizeX(label) + EditorScale.of(PADDING_X) * 2.0f;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        boolean primaryClicked = renderPart(id + "-primary", left, top, primaryWidth, height,
                ImDrawFlags.RoundCornersLeft);
        drawLabel(label, left, top, primaryWidth, height);
        boolean menuRequested = renderPart(id + "-menu", left + primaryWidth, top, arrowWidth, height,
                ImDrawFlags.RoundCornersRight);
        drawArrow(left + primaryWidth, top, arrowWidth, height);
        drawDivider(left + primaryWidth, top, height);
        ImGui.setCursorScreenPos(left, top);
        ImGui.dummy(primaryWidth + arrowWidth, height);
        return new Result(primaryClicked, menuRequested);
    }

    private static boolean renderPart(String id, float left, float top, float width, float height,
                                      int corners) {
        ImGui.setCursorScreenPos(left, top);
        boolean clicked = ImGui.invisibleButton(id, width, height);
        float emphasis = EditorMotion.towards(id, ImGui.isItemHovered());
        int background = EditorMotion.blend(EditorStyle.COLOR_WIDGET_BACKGROUND,
                EditorStyle.COLOR_WIDGET_HOVER, emphasis);
        ImGui.getWindowDrawList().addRectFilled(left, top, left + width, top + height,
                background, EditorScale.of(ROUNDING), corners);
        return clicked;
    }

    private static void drawLabel(String label, float left, float top, float width, float height) {
        float textX = left + (width - ImGui.calcTextSizeX(label)) * 0.5f;
        float textY = top + (height - ImGui.getTextLineHeight()) * 0.5f;
        ImGui.getWindowDrawList().addText(textX, textY, EditorStyle.COLOR_TEXT, label);
    }

    private static void drawArrow(float left, float top, float width, float height) {
        float centerX = left + width * 0.5f;
        float centerY = top + height * 0.5f;
        float half = EditorScale.of(ARROW_HALF_WIDTH);
        float depth = EditorScale.of(ARROW_HEIGHT);
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addTriangleFilled(centerX - half, centerY - depth * 0.5f,
                centerX + half, centerY - depth * 0.5f,
                centerX, centerY + depth, EditorStyle.COLOR_TEXT);
    }

    private static void drawDivider(float x, float top, float height) {
        float inset = EditorScale.of(DIVIDER_INSET);
        ImGui.getWindowDrawList().addLine(x, top + inset, x, top + height - inset,
                EditorStyle.COLOR_OUTLINE);
    }
}
