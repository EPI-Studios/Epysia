package fr.epistudio.epysia.editor.ui;

import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;

public final class TextFields {

    private TextFields() {
    }

    public static boolean inputSubmitted(String id, ImString buffer) {
        ImGui.inputText(id, buffer);
        return submitted();
    }

    public static boolean inputWithHintSubmitted(String id, String hint, ImString buffer) {
        ImGui.inputTextWithHint(id, hint, buffer);
        return submitted();
    }

    private static boolean submitted() {
        return ImGui.isItemFocused()
                && (ImGui.isKeyPressed(ImGuiKey.Enter) || ImGui.isKeyPressed(ImGuiKey.KeypadEnter));
    }
}
