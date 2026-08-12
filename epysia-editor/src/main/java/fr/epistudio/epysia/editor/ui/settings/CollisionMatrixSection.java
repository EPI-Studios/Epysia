package fr.epistudio.epysia.editor.ui.settings;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.project.EditorSettings;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

public final class CollisionMatrixSection {

    private static final int LAYER_NAME_CAPACITY = 64;
    private static final float NAME_FIELD_WIDTH = 150.0f;

    private final SettingsChrome chrome;
    private final ImString[] layerNames = new ImString[EditorSettings.LAYER_COUNT];

    private int[] collisionMatrix = new int[EditorSettings.LAYER_COUNT];

    public CollisionMatrixSection(SettingsChrome chrome) {
        this.chrome = chrome;
        for (int index = 0; index < layerNames.length; index++) {
            layerNames[index] = new ImString(LAYER_NAME_CAPACITY);
        }
    }

    public void load(EditorSettings settings) {
        collisionMatrix = settings.collisionMatrix();
        for (int index = 0; index < layerNames.length; index++) {
            layerNames[index].set(settings.layerNames().get(index));
        }
    }

    public EditorSettings build() {
        List<String> names = new ArrayList<>(layerNames.length);
        for (ImString layerName : layerNames) {
            names.add(layerName.get());
        }
        return new EditorSettings(names, collisionMatrix);
    }

    public void render() {
        if (chrome.skipWhileFiltering()) {
            return;
        }
        chrome.hint(TextKey.EDITOR_SETTINGS_DIALOG_LAYERS_HELP);
        ImGui.beginChild("##collision-grid", 0.0f, 0.0f);
        for (int row = 0; row < EditorSettings.LAYER_COUNT; row++) {
            renderRow(row);
        }
        ImGui.endChild();
    }

    private void renderRow(int row) {
        ImGui.pushID(row);
        ImGui.setNextItemWidth(EditorScale.of(NAME_FIELD_WIDTH));
        ImGui.inputText("##layer-name", layerNames[row]);
        for (int column = 0; column < EditorSettings.LAYER_COUNT; column++) {
            ImGui.sameLine();
            renderCell(row, column);
        }
        ImGui.popID();
    }

    private void renderCell(int row, int column) {
        ImGui.pushID(column);
        boolean checked = (collisionMatrix[row] & (1 << column)) != 0;
        if (ImGui.checkbox("##cell", checked)) {
            toggle(row, column, !checked);
        }
        ImGui.popID();
    }

    private void toggle(int row, int column, boolean enabled) {
        if (enabled) {
            collisionMatrix[row] |= (1 << column);
            collisionMatrix[column] |= (1 << row);
            return;
        }
        collisionMatrix[row] &= ~(1 << column);
        collisionMatrix[column] &= ~(1 << row);
    }
}
