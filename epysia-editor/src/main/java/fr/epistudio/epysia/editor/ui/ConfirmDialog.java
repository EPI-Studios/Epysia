package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

public final class ConfirmDialog {

    private final String title;
    private final String confirmLabel;
    private String message = "";
    private Runnable onConfirm = () -> {
    };
    private boolean openRequested;

    public ConfirmDialog(String title, String confirmLabel) {
        this.title = title;
        this.confirmLabel = confirmLabel;
    }

    public void open(String bodyMessage, Runnable confirmedAction) {
        message = bodyMessage;
        onConfirm = confirmedAction;
        openRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(title);
            openRequested = false;
        }
        if (!ImGui.beginPopupModal(title, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        ImGui.textUnformatted(message);
        ImGui.separator();
        renderButtons();
        ImGui.endPopup();
    }

    private void renderButtons() {
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_DANGER);
        boolean confirmed = ImGui.button(confirmLabel);
        ImGui.popStyleColor();
        ImGui.sameLine();
        boolean cancelled = ImGui.button("Cancel");
        if (confirmed) {
            ImGui.closeCurrentPopup();
            onConfirm.run();
        } else if (cancelled) {
            ImGui.closeCurrentPopup();
        }
    }
}
