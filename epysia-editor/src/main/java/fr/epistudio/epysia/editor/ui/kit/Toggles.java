package fr.epistudio.epysia.editor.ui.kit;

import fr.epistudio.epysia.editor.shell.EditorMotion;
import imgui.ImGui;

public final class Toggles {

    private Toggles() {
    }

    public static boolean text(String id, String label, boolean active) {
        float emphasis = EditorMotion.valueOf(id);
        ToggleStyle.push(active, emphasis);
        ImGui.pushID(id);
        boolean clicked = Toolbars.textButton(label);
        ImGui.popID();
        ToggleStyle.pop(active);
        EditorMotion.towards(id, ImGui.isItemHovered());
        return clicked;
    }

    public static boolean textSized(String id, String label, boolean active, float width, float height) {
        float emphasis = EditorMotion.valueOf(id);
        ToggleStyle.push(active, emphasis);
        ImGui.pushID(id);
        boolean clicked = ImGui.button(label, width, height);
        ImGui.popID();
        ToggleStyle.pop(active);
        EditorMotion.towards(id, ImGui.isItemHovered());
        return clicked;
    }
}
