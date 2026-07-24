package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TilemapLayer;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import fr.epistudio.epysia.editor.tilemap.TileTool;
import imgui.ImGui;

public final class TileToolBar {

    private static final float BUTTON_SIZE = 18.0f;

    private final IconWidgets icons;
    private final TileBrush brush;

    public TileToolBar(IconWidgets icons, TileBrush brush) {
        this.icons = icons;
        this.brush = brush;
    }

    public void render(SpriteTilemap tilemap) {
        for (TileTool tool : TileTool.values()) {
            renderToolButton(tool);
        }
        ImGui.newLine();
        renderClipboardState();
        renderLayerSelector(tilemap);
    }

    private void renderToolButton(TileTool tool) {
        if (icons.toggleButton("tool" + tool.name(), tool.icon(), BUTTON_SIZE, brush.tool() == tool)) {
            brush.setTool(tool);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tool.label());
        }
        ImGui.sameLine();
    }

    private void renderClipboardState() {
        icons.draw(EditorIcon.ACTION_COPY, BUTTON_SIZE);
        ImGui.sameLine();
        ImGui.textDisabled(brush.clipboardFilled()
                ? brush.clipboard().size() + " cells copied"
                : "Select cells then press C to copy");
    }

    private void renderLayerSelector(SpriteTilemap tilemap) {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        TilemapLayer active = tilemap.layer(brush.layerIndex());
        if (!ImGui.beginCombo("##paintLayer", "Painting on " + active.name())) {
            return;
        }
        for (int layerIndex = 0; layerIndex < tilemap.layerCount(); layerIndex++) {
            if (ImGui.selectable(tilemap.layer(layerIndex).name(), layerIndex == brush.layerIndex())) {
                brush.setLayerIndex(layerIndex);
            }
        }
        ImGui.endCombo();
    }
}
