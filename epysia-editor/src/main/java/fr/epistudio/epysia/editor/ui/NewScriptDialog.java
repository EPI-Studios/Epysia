package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.ui.kit.Dialogs;
import fr.epistudio.epysia.editor.ui.kit.SegmentedControl;
import fr.epistudio.epysia.editor.ui.kit.Sections;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.scripting.compile.ScriptLanguage;
import fr.epistudio.epysia.scripting.compile.ScriptLanguages;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class NewScriptDialog {

    private static final String POPUP_ID = "##new-script-dialog";
    private static final int NAME_CAPACITY = 128;
    private static final float DIALOG_WIDTH = 420.0f;

    private final ScriptLanguages scriptLanguages;
    private final BiConsumer<ScriptLanguage, String> onCreate;
    private final ImString className = new ImString(NAME_CAPACITY);
    private int selectedLanguage;
    private boolean openRequested;
    private boolean focusRequested;

    public NewScriptDialog(ScriptLanguages scriptLanguages, BiConsumer<ScriptLanguage, String> onCreate) {
        this.scriptLanguages = scriptLanguages;
        this.onCreate = onCreate;
    }

    public void open() {
        className.set(I18n.translate(TextKey.EDITOR_EDITOR_VIEW_SCRIPT_DEFAULT_NAME));
        selectedLanguage = languages().indexOf(scriptLanguages.defaultLanguage());
        openRequested = true;
        focusRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        if (!Dialogs.begin(POPUP_ID, DIALOG_WIDTH)) {
            return;
        }
        Dialogs.title(I18n.translate(TextKey.EDITOR_NEW_SCRIPT_DIALOG_TITLE));
        renderNameField();
        renderLanguages();
        renderFileName();
        Dialogs.gap();
        renderFooter();
        Dialogs.end();
    }

    private void renderNameField() {
        Sections.caption(I18n.translate(TextKey.EDITOR_NEW_SCRIPT_DIALOG_CLASS_NAME));
        if (focusRequested) {
            ImGui.setKeyboardFocusHere();
            focusRequested = false;
        }
        if (TextFields.underlinedSubmitted("##script-class-name", "", className,
                ImGui.getContentRegionAvailX())) {
            create();
        }
    }

    private void renderLanguages() {
        List<ScriptLanguage> available = languages();
        if (available.size() < 2) {
            return;
        }
        Dialogs.gap();
        Sections.caption(I18n.translate(TextKey.EDITOR_NEW_SCRIPT_DIALOG_LANGUAGE));
        selectedLanguage = SegmentedControl.render("##script-language", displayNames(available),
                Math.clamp(selectedLanguage, 0, available.size() - 1));
    }

    private void renderFileName() {
        Dialogs.gap();
        Texts.muted(fileName());
    }

    private void renderFooter() {
        Dialogs.alignFooter(2);
        if (Dialogs.button(I18n.translate(TextKey.EDITOR_NAME_DIALOG_CANCEL))) {
            ImGui.closeCurrentPopup();
        }
        ImGui.sameLine();
        if (Dialogs.primaryButton(I18n.translate(TextKey.EDITOR_NEW_SCRIPT_DIALOG_CREATE), isNameValid())) {
            create();
        }
    }

    private void create() {
        if (!isNameValid()) {
            return;
        }
        ImGui.closeCurrentPopup();
        onCreate.accept(language(), trimmedName());
    }

    private String fileName() {
        return isNameValid()
                ? trimmedName() + language().sourceExtension()
                : I18n.translate(TextKey.EDITOR_NEW_SCRIPT_DIALOG_INVALID_NAME);
    }

    private boolean isNameValid() {
        return EditorView.isScriptClassName(trimmedName());
    }

    private String trimmedName() {
        return className.get().replace("\0", "").strip();
    }

    private ScriptLanguage language() {
        List<ScriptLanguage> available = languages();
        return available.get(Math.clamp(selectedLanguage, 0, available.size() - 1));
    }

    private List<ScriptLanguage> languages() {
        return scriptLanguages.authoringOrder();
    }

    private static List<String> displayNames(List<ScriptLanguage> languages) {
        List<String> names = new ArrayList<>(languages.size());
        for (ScriptLanguage language : languages) {
            names.add(language.displayName());
        }
        return names;
    }
}
