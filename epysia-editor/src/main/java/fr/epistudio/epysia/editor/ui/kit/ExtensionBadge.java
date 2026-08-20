package fr.epistudio.epysia.editor.ui.kit;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImGui;
import imgui.ImVec2;

import java.util.Locale;

public final class ExtensionBadge {

    private static final int MAXIMUM_LETTERS = 4;
    private static final float PADDING_X = 3.0f;
    private static final float PADDING_Y = 1.0f;
    private static final float CORNER_ROUNDING = 2.0f;
    private static final float BOTTOM_INSET = 4.0f;
    private static final float CORNER_RATIO = 0.45f;
    private static final int PALETTE_MASK = 0x7FFFFFFF;

    private static final int[] PALETTE = {
            EditorStyle.rgb(94, 129, 172),
            EditorStyle.rgb(163, 190, 140),
            EditorStyle.rgb(208, 135, 112),
            EditorStyle.rgb(180, 142, 173),
            EditorStyle.rgb(143, 188, 187),
            EditorStyle.rgb(235, 203, 139),
    };

    private ExtensionBadge() {
    }

    public static void draw(String assetPath, float iconX, float iconY, float iconSize) {
        String label = extensionOf(assetPath);
        if (label.isEmpty()) {
            return;
        }
        ImVec2 textSize = ImGui.calcTextSize(label);
        float paddingX = EditorScale.of(PADDING_X);
        float paddingY = EditorScale.of(PADDING_Y);
        float badgeWidth = textSize.x + paddingX * 2.0f;
        float badgeHeight = textSize.y + paddingY * 2.0f;
        if (badgeWidth > iconSize || badgeHeight > iconSize) {
            paintCorner(iconX, iconY, iconSize, colorFor(label));
            return;
        }
        float left = iconX + (iconSize - badgeWidth) * 0.5f;
        float top = iconY + iconSize - badgeHeight - EditorScale.of(BOTTOM_INSET);
        paint(label, left, top, badgeWidth, badgeHeight, paddingX, paddingY, colorFor(label));
    }

    private static void paintCorner(float iconX, float iconY, float iconSize, int color) {
        float size = iconSize * CORNER_RATIO;
        float left = iconX + iconSize - size;
        float top = iconY + iconSize - size;
        ImGui.getWindowDrawList().addRectFilled(left, top, left + size, top + size, color,
                EditorScale.of(CORNER_ROUNDING));
    }

    private static void paint(String label, float left, float top, float width, float height,
                              float paddingX, float paddingY, int color) {
        ImGui.getWindowDrawList().addRectFilled(left, top, left + width, top + height,
                color, EditorScale.of(CORNER_ROUNDING));
        ImGui.getWindowDrawList().addText(left + paddingX, top + paddingY,
                EditorStyle.COLOR_WINDOW_BACKGROUND, label);
    }

    private static String extensionOf(String assetPath) {
        int separator = assetPath.lastIndexOf('.');
        if (separator < 0 || separator == assetPath.length() - 1) {
            return "";
        }
        String extension = assetPath.substring(separator + 1).toUpperCase(Locale.ROOT);
        return extension.length() > MAXIMUM_LETTERS ? extension.substring(0, MAXIMUM_LETTERS) : extension;
    }

    private static int colorFor(String label) {
        return PALETTE[(label.hashCode() & PALETTE_MASK) % PALETTE.length];
    }
}
