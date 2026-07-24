package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TilemapLayer;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import imgui.ImGui;
import imgui.type.ImString;

public final class TileLayersSection {

    private static final float BUTTON_SIZE = 16.0f;
    private static final int NAME_CAPACITY = 64;

    private final IconWidgets icons;
    private final TileBrush brush;
    private final ImString renameBuffer = new ImString(NAME_CAPACITY);
    private int renamingLayer = -1;

    public TileLayersSection(IconWidgets icons, TileBrush brush) {
        this.icons = icons;
        this.brush = brush;
    }

    public boolean render(SpriteTilemap tilemap) {
        if (!ImGui.collapsingHeader("Layers")) {
            return false;
        }
        boolean changed = renderAddButton(tilemap);
        for (int layerIndex = 0; layerIndex < tilemap.layerCount(); layerIndex++) {
            changed |= renderLayerRow(tilemap, layerIndex);
        }
        return changed;
    }

    private boolean renderAddButton(SpriteTilemap tilemap) {
        boolean clicked = icons.iconButton("addLayer", EditorIcon.ADD, BUTTON_SIZE);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add a layer. Lower sorting order draws first, so put the background there.");
        }
        if (!clicked) {
            return false;
        }
        tilemap.addLayer("Layer " + (tilemap.layerCount() + 1));
        brush.setLayerIndex(tilemap.layerCount() - 1);
        return true;
    }

    private boolean renderLayerRow(SpriteTilemap tilemap, int layerIndex) {
        TilemapLayer layer = tilemap.layer(layerIndex);
        ImGui.pushID("layer" + layerIndex);
        boolean changed = renderVisibilityToggle(tilemap, layer);
        ImGui.sameLine();
        changed |= renderCollisionToggle(tilemap, layer);
        ImGui.sameLine();
        changed |= renderNameCell(tilemap, layerIndex, layer);
        ImGui.sameLine();
        changed |= renderRemoveButton(tilemap, layerIndex);
        ImGui.popID();
        return changed;
    }

    private boolean renderVisibilityToggle(SpriteTilemap tilemap, TilemapLayer layer) {
        EditorIcon icon = layer.visible() ? EditorIcon.VISIBILITY_VISIBLE : EditorIcon.VISIBILITY_HIDDEN;
        boolean clicked = icons.iconButton("visible", icon, BUTTON_SIZE);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(layer.visible() ? "Hide this layer" : "Show this layer");
        }
        if (!clicked) {
            return false;
        }
        layer.setVisible(!layer.visible());
        tilemap.touch();
        return true;
    }

    private boolean renderCollisionToggle(SpriteTilemap tilemap, TilemapLayer layer) {
        boolean clicked = icons.toggleButton("collision", EditorIcon.COLLISION_SHAPE_3D, BUTTON_SIZE,
                layer.collisionEnabled());
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(layer.collisionEnabled()
                    ? "This layer builds physics. Turn it off for pure decoration."
                    : "This layer is decoration only.");
        }
        if (!clicked) {
            return false;
        }
        layer.setCollisionEnabled(!layer.collisionEnabled());
        tilemap.touch();
        return true;
    }

    private boolean renderNameCell(SpriteTilemap tilemap, int layerIndex, TilemapLayer layer) {
        if (renamingLayer == layerIndex) {
            return renderRenameField(tilemap, layer);
        }
        if (ImGui.selectable(layer.name(), layerIndex == brush.layerIndex(), 0, 120.0f, 0.0f)) {
            brush.setLayerIndex(layerIndex);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Click to paint on it, double click to rename.");
        }
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            renamingLayer = layerIndex;
            renameBuffer.set(layer.name());
        }
        return false;
    }

    private boolean renderRenameField(SpriteTilemap tilemap, TilemapLayer layer) {
        ImGui.setNextItemWidth(120.0f);
        boolean committed = ImGui.inputText("##rename", renameBuffer, imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue);
        if (!committed && ImGui.isItemDeactivated()) {
            renamingLayer = -1;
            return false;
        }
        if (!committed) {
            return false;
        }
        layer.setName(renameBuffer.get());
        renamingLayer = -1;
        tilemap.touch();
        return true;
    }

    private boolean renderRemoveButton(SpriteTilemap tilemap, int layerIndex) {
        ImGui.beginDisabled(tilemap.layerCount() <= 1);
        boolean clicked = icons.iconButton("removeLayer", EditorIcon.REMOVE, BUTTON_SIZE);
        ImGui.endDisabled();
        if (!clicked) {
            return false;
        }
        tilemap.removeLayer(layerIndex);
        brush.setLayerIndex(Math.min(brush.layerIndex(), tilemap.layerCount() - 1));
        return true;
    }
}
