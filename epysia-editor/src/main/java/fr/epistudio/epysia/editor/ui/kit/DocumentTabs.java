package fr.epistudio.epysia.editor.ui.kit;

import fr.epistudio.epysia.editor.shell.EditorScale;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;

public final class DocumentTabs {

    private static final float ICON_SIZE = 13.0f;
    private static final float ICON_LEADING_GAP = 4.0f;
    private static final String ICON_RESERVE = "     ";

    private DocumentTabs() {
    }

    public static String reserveIconSpace(String label) {
        return ICON_RESERVE + label;
    }

    public static void decorate(int iconTextureId) {
        drawIcon(iconTextureId);
    }

    public static boolean closeRequestedByMiddleClick() {
        return ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Middle);
    }

    private static void drawIcon(int iconTextureId) {
        if (iconTextureId == 0) {
            return;
        }
        float size = EditorScale.of(ICON_SIZE);
        float left = ImGui.getItemRectMinX() + EditorScale.of(ICON_LEADING_GAP);
        float top = ImGui.getItemRectMinY()
                + (ImGui.getItemRectMaxY() - ImGui.getItemRectMinY() - size) * 0.5f;
        ImGui.getWindowDrawList().addImage(iconTextureId, left, top, left + size, top + size);
    }
}
