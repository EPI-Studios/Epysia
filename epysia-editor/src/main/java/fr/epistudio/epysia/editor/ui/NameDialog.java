package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.ui.kit.Dialogs;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.function.Consumer;

public final class NameDialog {

    private static final int NAME_CAPACITY = 256;
    private static final float DIALOG_WIDTH = 400.0f;

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
        if (!Dialogs.begin(popupId, DIALOG_WIDTH)) {
            return;
        }
        Dialogs.title(title);
        renderField();
        Dialogs.gap();
        renderFooter();
        Dialogs.end();
    }

    private void renderField() {
        if (focusRequested) {
            ImGui.setKeyboardFocusHere();
            focusRequested = false;
        }
        if (TextFields.underlinedSubmitted("##name", "", nameInput, ImGui.getContentRegionAvailX())) {
            accept();
        }
    }

    private void renderFooter() {
        Dialogs.alignFooter(2);
        if (Dialogs.button(I18n.label(TextKey.EDITOR_NAME_DIALOG_CANCEL, "name-dialog-cancel"))) {
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton(I18n.label(TextKey.EDITOR_NAME_DIALOG_OK, "name-dialog-ok"),
                !trimmedName().isEmpty())) {
            accept();
        }
    }

    private void accept() {
        String value = trimmedName();
        if (value.isEmpty()) {
            return;
        }
        ImGui.closeCurrentPopup();
        onAccept.accept(value);
    }

    private String trimmedName() {
        return nameInput.get().replace("\0", "").strip();
    }
}
