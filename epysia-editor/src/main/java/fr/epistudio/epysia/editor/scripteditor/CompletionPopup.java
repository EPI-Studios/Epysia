package fr.epistudio.epysia.editor.scripteditor;

import fr.epistudio.epysia.editor.shell.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;
import java.util.Optional;

public final class CompletionPopup {

    public enum KeyAction {
        NONE, ACCEPT, CLOSE, NAVIGATE
    }

    private static final int MAX_VISIBLE_ROWS = 10;
    private static final float POPUP_WIDTH = 420.0f;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize
            | ImGuiWindowFlags.NoMove
            | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav
            | ImGuiWindowFlags.NoDocking;

    private List<CompletionSymbol> items = List.of();
    private int selectedIndex;
    private float positionX;
    private float positionY;
    private boolean visible;
    private boolean scrollToSelection;

    public void show(List<CompletionSymbol> newItems, float x, float y) {
        if (newItems.isEmpty()) {
            hide();
            return;
        }
        items = newItems;
        selectedIndex = 0;
        positionX = x;
        positionY = y;
        visible = true;
        scrollToSelection = true;
    }

    public void hide() {
        visible = false;
        items = List.of();
    }

    public boolean isVisible() {
        return visible;
    }

    public Optional<CompletionSymbol> selected() {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(selectedIndex));
    }

    public KeyAction handleKeys() {
        if (!visible) {
            return KeyAction.NONE;
        }
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            return KeyAction.CLOSE;
        }
        if (ImGui.isKeyPressed(ImGuiKey.Enter) || ImGui.isKeyPressed(ImGuiKey.Tab)) {
            return KeyAction.ACCEPT;
        }
        return navigationAction();
    }

    private KeyAction navigationAction() {
        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
            moveSelection(-1);
            return KeyAction.NAVIGATE;
        }
        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            moveSelection(1);
            return KeyAction.NAVIGATE;
        }
        return KeyAction.NONE;
    }

    private void moveSelection(int delta) {
        selectedIndex = Math.floorMod(selectedIndex + delta, items.size());
        scrollToSelection = true;
    }

    public Optional<CompletionSymbol> render() {
        if (!visible) {
            return Optional.empty();
        }
        ImGui.setNextWindowPos(positionX, positionY);
        ImGui.setNextWindowSize(POPUP_WIDTH, popupHeight());
        ImGui.begin("##completion-popup", WINDOW_FLAGS);
        Optional<CompletionSymbol> clicked = renderRows();
        ImGui.end();
        return clicked;
    }

    private float popupHeight() {
        int visibleRows = Math.min(items.size(), MAX_VISIBLE_ROWS);
        return visibleRows * ImGui.getTextLineHeightWithSpacing()
                + 2.0f * EditorStyle.WINDOW_PADDING;
    }

    private Optional<CompletionSymbol> renderRows() {
        Optional<CompletionSymbol> clicked = Optional.empty();
        for (int index = 0; index < items.size(); index++) {
            CompletionSymbol symbol = items.get(index);
            renderTag(symbol.kind());
            ImGui.sameLine();
            if (ImGui.selectable(symbol.label() + "##" + index, index == selectedIndex)) {
                clicked = Optional.of(symbol);
            }
            if (index == selectedIndex && scrollToSelection) {
                ImGui.setScrollHereY();
            }
        }
        scrollToSelection = false;
        return clicked;
    }

    private void renderTag(CompletionKind kind) {
        ImGui.pushStyleColor(ImGuiCol.Text, tagColor(kind));
        ImGui.text(kind.tag());
        ImGui.popStyleColor();
    }

    private static int tagColor(CompletionKind kind) {
        return switch (kind) {
            case KEYWORD -> EditorStyle.COLOR_ACCENT_HOVER;
            case TYPE -> EditorStyle.COLOR_SUCCESS;
            case METHOD -> EditorStyle.COLOR_SYSTEM;
            case FIELD -> EditorStyle.COLOR_WARNING;
            case LOCAL -> EditorStyle.COLOR_TEXT_MUTED;
        };
    }
}
