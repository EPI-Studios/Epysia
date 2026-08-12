package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.editor.shell.EditorScale;
import fr.epistudio.epysia.i18n.I18n;
import fr.epistudio.epysia.i18n.TextKey;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TilemapLayer;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import fr.epistudio.epysia.editor.tilemap.TileTool;
import fr.epistudio.epysia.editor.ui.kit.Texts;
import imgui.ImGui;

public final class TileToolBar {

    private static final float BUTTON_SIZE = 24.0f;
    private static final int TOOLS_PER_ROW = 2;

    private final IconWidgets icons;
    private final TileBrush brush;

    public TileToolBar(IconWidgets icons, TileBrush brush) {
        this.icons = icons;
        this.brush = brush;
    }

    public boolean renderTools() {
        boolean picked = false;
        TileTool[] tools = TileTool.values();
        for (int index = 0; index < tools.length; index++) {
            picked |= renderToolButton(tools[index]);
            if (index % TOOLS_PER_ROW != TOOLS_PER_ROW - 1) {
                ImGui.sameLine();
            }
        }
        ImGui.newLine();
        return picked;
    }

    private boolean renderToolButton(TileTool tool) {
        boolean clicked = icons.toggleButton("tool" + tool.name(), tool.icon(), EditorScale.of(BUTTON_SIZE), brush.tool() == tool);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tool.label() + "  (" + tool.shortcut() + ")\n" + tool.hint());
        }
        if (clicked) {
            brush.setTool(tool);
        }
        return clicked;
    }

    public void renderClipboardState() {
        Texts.muted(brush.tool().label());
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(brush.tool().hint());
        }
        Texts.muted(brush.clipboardFilled() ? brush.clipboard().size() + " copied" : "clipboard empty");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_TILE_TOOL_BAR_SELECTION_HINT));
        }
    }

    public void renderLayerSelector(SpriteTilemap tilemap) {
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        TilemapLayer active = tilemap.layer(brush.layerIndex());
        boolean open = ImGui.beginCombo("##paintLayer", "Painting on " + active.name());
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_TILE_TOOL_BAR_LAYER_HINT));
        }
        if (!open) {
            return;
        }
        for (int layerIndex = 0; layerIndex < tilemap.layerCount(); layerIndex++) {
            if (ImGui.selectable(tilemap.layer(layerIndex).name(), layerIndex == brush.layerIndex())) {
                brush.setLayerIndex(layerIndex);
            }
        }
        ImGui.endCombo();
    }

    public void renderMatchModeLegend() {
        icons.draw(EditorIcon.TERRAIN_CONNECT, EditorScale.of(BUTTON_SIZE));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(I18n.translate(TextKey.EDITOR_TILE_TOOL_BAR_TERRAIN_HINT));
        }
    }
}
