package fr.epistudio.epysia.editor.ui;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.function.Consumer;

public final class NameDialog {

    private static final int NAME_CAPACITY = 256;
    private static final float FIELD_WIDTH = 320.0f;

    private final String popupId;
    private final ImString nameInput = new ImString(NAME_CAPACITY);
    private String title = "";
    private Consumer<String> onAccept = name -> {
    };
    private boolean openRequested;
    private boolean focusRequested;

    public NameDialog(String popupId) {
        this.popupId = popupId;
    }

    public void open(String dialogTitle, String initialValue, Consumer<String> acceptHandler) {
        title = dialogTitle;
        nameInput.set(initialValue);
        onAccept = acceptHandler;
        openRequested = true;
        focusRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(popupId);
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.textUnformatted(title);
        renderField();
        renderButtons();
        ImGui.endPopup();
    }

    private void renderField() {
        if (focusRequested) {
            ImGui.setKeyboardFocusHere();
            focusRequested = false;
        }
        ImGui.setNextItemWidth(FIELD_WIDTH);
        if (ImGui.inputText("##name", nameInput, ImGuiInputTextFlags.EnterReturnsTrue)) {
            accept();
        }
    }

    private void renderButtons() {
        if (ImGui.button("OK")) {
            accept();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel")) {
            ImGui.closeCurrentPopup();
        }
    }

    private void accept() {
        String value = nameInput.get().replace("\0", "").strip();
        if (value.isEmpty()) {
            return;
        }
        ImGui.closeCurrentPopup();
        onAccept.accept(value);
    }
}
