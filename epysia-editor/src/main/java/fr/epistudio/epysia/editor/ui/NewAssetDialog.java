package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class NewAssetDialog {

    public record AssetKind(String label, String category, String description, EditorIcon icon,
                            String defaultName, Consumer<String> create) {
    }

    private static final int TEXT_CAPACITY = 256;
    private static final float DIALOG_WIDTH = 520.0f;
    private static final float LIST_HEIGHT = 300.0f;
    private static final float ROW_HEIGHT = 26.0f;
    private static final float ICON_SIZE = 15.0f;
    private static final float ROW_PADDING = 6.0f;
    private static final float ICON_TEXT_GAP = 8.0f;
    private static final float CATEGORY_TOP_MARGIN = 6.0f;

    private final String popupId;
    private final IconWidgets icons;
    private final List<AssetKind> kinds = new ArrayList<>();
    private final ImString searchInput = new ImString(TEXT_CAPACITY);
    private final ImString nameInput = new ImString(TEXT_CAPACITY);
    private int selectedIndex;
    private boolean openRequested;
    private boolean searchFocusRequested;

    public NewAssetDialog(String popupId, IconWidgets icons) {
        this.popupId = popupId;
        this.icons = icons;
    }

    public void setKinds(List<AssetKind> availableKinds) {
        kinds.clear();
        kinds.addAll(availableKinds);
    }

    public void open() {
        searchInput.set("");
        selectedIndex = 0;
        applySelectedDefaultName();
        openRequested = true;
        searchFocusRequested = true;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(popupId);
            openRequested = false;
        }
        ImGui.setNextWindowSize(DIALOG_WIDTH, 0.0f);
        if (!ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            return;
        }
        renderSearchField();
        List<AssetKind> matches = matchingKinds();
        renderKindList(matches);
        renderNameField(matches);
        ImGui.separator();
        renderButtons(matches);
        ImGui.endPopup();
    }

    private void renderSearchField() {
        if (searchFocusRequested) {
            ImGui.setKeyboardFocusHere();
            searchFocusRequested = false;
        }
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.inputTextWithHint("##new-asset-search", "Search", searchInput)) {
            selectedIndex = 0;
            applySelectedDefaultName();
        }
        ImGui.dummy(0.0f, 2.0f);
    }

    private void renderKindList(List<AssetKind> matches) {
        ImGui.beginChild("##new-asset-list", 0.0f, LIST_HEIGHT, true);
        if (matches.isEmpty()) {
            ImGui.textDisabled("Nothing matches.");
        }
        String renderedCategory = "";
        for (int index = 0; index < matches.size(); index++) {
            AssetKind kind = matches.get(index);
            if (!kind.category().equals(renderedCategory)) {
                renderedCategory = kind.category();
                renderCategoryHeader(renderedCategory, index > 0);
            }
            renderKindRow(matches, index, kind);
        }
        ImGui.endChild();
        ImGui.dummy(0.0f, 2.0f);
    }

    private static void renderCategoryHeader(String category, boolean spaceAbove) {
        if (spaceAbove) {
            ImGui.dummy(0.0f, CATEGORY_TOP_MARGIN);
        }
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted(category.toUpperCase(Locale.ROOT));
        ImGui.popStyleColor();
    }

    private void renderKindRow(List<AssetKind> matches, int index, AssetKind kind) {
        ImGui.pushID(index);
        float rowStartY = ImGui.getCursorPosY();
        if (ImGui.selectable("##row", index == selectedIndex,
                ImGuiSelectableFlags.AllowDoubleClick, 0.0f, ROW_HEIGHT)) {
            selectedIndex = index;
            nameInput.set(kind.defaultName());
            if (ImGui.isMouseDoubleClicked(0)) {
                accept(matches);
            }
        }
        renderRowContent(kind, rowStartY);
        ImGui.popID();
    }

    private void renderRowContent(AssetKind kind, float rowStartY) {
        float iconOffset = (ROW_HEIGHT - ICON_SIZE) * 0.5f;
        ImGui.setCursorPosY(rowStartY + iconOffset);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + ROW_PADDING);
        icons.draw(kind.icon(), ICON_SIZE);
        ImGui.sameLine(0.0f, ICON_TEXT_GAP);
        ImGui.setCursorPosY(rowStartY + (ROW_HEIGHT - ImGui.getTextLineHeight()) * 0.5f);
        ImGui.textUnformatted(kind.label());
        ImGui.sameLine(0.0f, ICON_TEXT_GAP);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted(kind.description());
        ImGui.popStyleColor();
        ImGui.setCursorPosY(rowStartY + ROW_HEIGHT);
    }

    private void renderNameField(List<AssetKind> matches) {
        ImGui.alignTextToFramePadding();
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted("Name");
        ImGui.popStyleColor();
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1.0f);
        if (TextFields.inputSubmitted("##new-asset-name", nameInput)) {
            accept(matches);
        }
    }

    private void renderButtons(List<AssetKind> matches) {
        boolean creatable = !matches.isEmpty() && !trimmedName().isEmpty();
        ImGui.beginDisabled(!creatable);
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_ACCENT_HOVER);
        if (ImGui.button("Create", 96.0f, 0.0f)) {
            accept(matches);
        }
        ImGui.popStyleColor(2);
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Cancel", 96.0f, 0.0f)) {
            ImGui.closeCurrentPopup();
        }
    }

    private List<AssetKind> matchingKinds() {
        String query = searchInput.get().replace("\0", "").strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.copyOf(kinds);
        }
        List<AssetKind> matches = new ArrayList<>();
        for (AssetKind kind : kinds) {
            if (matches(kind, query)) {
                matches.add(kind);
            }
        }
        return matches;
    }

    private static boolean matches(AssetKind kind, String query) {
        return kind.label().toLowerCase(Locale.ROOT).contains(query)
                || kind.description().toLowerCase(Locale.ROOT).contains(query)
                || kind.category().toLowerCase(Locale.ROOT).contains(query);
    }

    private void applySelectedDefaultName() {
        List<AssetKind> matches = matchingKinds();
        if (!matches.isEmpty()) {
            nameInput.set(matches.get(Math.min(selectedIndex, matches.size() - 1)).defaultName());
        }
    }

    private String trimmedName() {
        return nameInput.get().replace("\0", "").strip();
    }

    private void accept(List<AssetKind> matches) {
        String name = trimmedName();
        if (matches.isEmpty() || name.isEmpty()) {
            return;
        }
        AssetKind kind = matches.get(Math.min(selectedIndex, matches.size() - 1));
        ImGui.closeCurrentPopup();
        kind.create().accept(name);
    }
}
