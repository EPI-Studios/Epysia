package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.commands.CommandRegistry;
import fr.epistudio.epysia.editor.commands.EditorCommand;
import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.List;

public final class CommandPaletteView {

    private static final String POPUP_ID = "##command-palette";
    private static final float WIDTH = 620.0f;
    private static final float ROW_LIMIT = 12.0f;
    private static final float TOP_MARGIN = 120.0f;
    private static final float SHORTCUT_MARGIN = 16.0f;
    private static final int QUERY_CAPACITY = 128;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoNav;

    private final ImString query = new ImString(QUERY_CAPACITY);
    private boolean opening;
    private boolean visible;
    private int selectedIndex;

    public void open() {
        query.set("");
        selectedIndex = 0;
        opening = true;
        visible = true;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(CommandRegistry registry) {
        if (opening) {
            ImGui.openPopup(POPUP_ID);
        }
        if (!visible) {
            return;
        }
        placeNextWindow();
        if (!ImGui.beginPopup(POPUP_ID, WINDOW_FLAGS)) {
            visible = false;
            opening = false;
            return;
        }
        List<EditorCommand> results = registry.matching(query.get());
        boolean submitted = renderQueryField();
        moveSelection(results.size());
        renderResults(results);
        if (submitted) {
            runSelected(results);
        }
        ImGui.endPopup();
        opening = false;
    }

    private void placeNextWindow() {
        float centerX = ImGui.getMainViewport().getWorkPosX() + ImGui.getMainViewport().getWorkSizeX() * 0.5f;
        float top = ImGui.getMainViewport().getWorkPosY() + EditorScale.of(TOP_MARGIN);
        ImGui.setNextWindowPos(centerX, top, ImGuiCond.Always, 0.5f, 0.0f);
        ImGui.setNextWindowSize(EditorScale.of(WIDTH), 0.0f);
    }

    private boolean renderQueryField() {
        if (opening) {
            ImGui.setKeyboardFocusHere();
        }
        ImGui.setNextItemWidth(-1.0f);
        return ImGui.inputTextWithHint("##command-query",
                I18n.translate(TextKey.EDITOR_COMMAND_PALETTE_HINT), query,
                ImGuiInputTextFlags.EnterReturnsTrue);
    }

    private void moveSelection(int count) {
        if (count == 0) {
            selectedIndex = 0;
            return;
        }
        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            selectedIndex = Math.floorMod(selectedIndex + 1, count);
        }
        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
            selectedIndex = Math.floorMod(selectedIndex - 1, count);
        }
        selectedIndex = Math.clamp(selectedIndex, 0, count - 1);
    }

    private void renderResults(List<EditorCommand> results) {
        if (results.isEmpty()) {
            Texts.muted(I18n.translate(TextKey.EDITOR_COMMAND_PALETTE_EMPTY));
            return;
        }
        ImGui.beginChild("##command-results", 0.0f,
                Math.min(results.size(), ROW_LIMIT) * ImGui.getTextLineHeightWithSpacing());
        for (int index = 0; index < results.size(); index++) {
            renderRow(results.get(index), index);
        }
        ImGui.endChild();
    }

    private void renderRow(EditorCommand command, int index) {
        boolean selected = index == selectedIndex;
        if (!command.isAvailable()) {
            ImGui.beginDisabled();
        }
        if (ImGui.selectable(command.searchLabel() + "##command-" + command.id(), selected)) {
            selectedIndex = index;
            execute(command);
        }
        if (selected && ImGui.isWindowAppearing()) {
            ImGui.setScrollHereY();
        }
        if (!command.isAvailable()) {
            ImGui.endDisabled();
        }
        renderShortcut(command);
    }

    private void renderShortcut(EditorCommand command) {
        if (command.shortcut().isEmpty()) {
            return;
        }
        float offset = ImGui.getContentRegionAvailX()
                - ImGui.calcTextSizeX(command.shortcut()) - EditorScale.of(SHORTCUT_MARGIN);
        ImGui.sameLine(offset);
        Texts.colored(EditorStyle.COLOR_TEXT_MUTED, command.shortcut());
    }

    private void runSelected(List<EditorCommand> results) {
        if (selectedIndex < 0 || selectedIndex >= results.size()) {
            return;
        }
        execute(results.get(selectedIndex));
    }

    private void execute(EditorCommand command) {
        if (!command.isAvailable()) {
            return;
        }
        ImGui.closeCurrentPopup();
        visible = false;
        command.run();
    }
}
