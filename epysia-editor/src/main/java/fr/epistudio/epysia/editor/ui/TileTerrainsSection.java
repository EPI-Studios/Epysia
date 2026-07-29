package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TerrainDefinition;
import fr.epistudio.epysia.assets.epytilemap.TileData;
import fr.epistudio.epysia.assets.epytilemap.TerrainMatchMode;
import fr.epistudio.epysia.editor.icons.EditorIcon;
import fr.epistudio.epysia.editor.icons.IconWidgets;
import fr.epistudio.epysia.editor.tilemap.TileBrush;
import imgui.ImGui;

public final class TileTerrainsSection {

    private static final float BUTTON_SIZE = 16.0f;

    private final IconWidgets icons;
    private final TileBrush brush;

    public TileTerrainsSection(IconWidgets icons, TileBrush brush) {
        this.icons = icons;
        this.brush = brush;
    }

    public boolean render(SpriteTilemap tilemap) {
        if (!ImGui.collapsingHeader("Terrains")) {
            return false;
        }
        boolean changed = renderMatchModes(tilemap);
        changed |= renderAddButton(tilemap);
        for (int terrainIndex = 0; terrainIndex < tilemap.terrains().size(); terrainIndex++) {
            changed |= renderTerrainRow(tilemap, terrainIndex);
        }
        return changed;
    }

    private boolean renderMatchModes(SpriteTilemap tilemap) {
        boolean changed = renderMatchModeButton(tilemap, TerrainMatchMode.CORNERS_AND_SIDES,
                EditorIcon.TERRAIN_MATCH_CORNERS_AND_SIDES);
        ImGui.sameLine();
        changed |= renderMatchModeButton(tilemap, TerrainMatchMode.CORNERS, EditorIcon.TERRAIN_MATCH_CORNERS);
        ImGui.sameLine();
        changed |= renderMatchModeButton(tilemap, TerrainMatchMode.SIDES, EditorIcon.TERRAIN_MATCH_SIDES);
        return changed;
    }

    private boolean renderMatchModeButton(SpriteTilemap tilemap, TerrainMatchMode mode, EditorIcon icon) {
        boolean active = tilemap.terrainMatchMode() == mode;
        boolean clicked = icons.toggleButton("mode" + mode.name(), icon, BUTTON_SIZE, active);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltipFor(mode));
        }
        if (!clicked) {
            return false;
        }
        tilemap.setTerrainMatchMode(mode);
        return true;
    }

    private static int configuredTileCount(SpriteTilemap tilemap, int terrainIndex) {
        int count = 0;
        for (TileData data : tilemap.tileDataByIndex().values()) {
            if (data.terrain() == terrainIndex) {
                count++;
            }
        }
        return count;
    }

    private static String tooltipFor(TerrainMatchMode mode) {
        return switch (mode) {
            case CORNERS_AND_SIDES -> "Tiles match on all eight neighbours. Richest, needs the most tiles.";
            case CORNERS -> "Tiles match on their four corners only.";
            case SIDES -> "Tiles match on their four sides only. Simplest, works with a sixteen tile blob.";
        };
    }

    private boolean renderAddButton(SpriteTilemap tilemap) {
        boolean clicked = icons.iconButton("addTerrain", EditorIcon.ADD, BUTTON_SIZE);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add a terrain, then mark which sides of each tile belong to it in Setup.");
        }
        if (!clicked) {
            return false;
        }
        tilemap.addTerrain(TerrainDefinition.named("Terrain " + tilemap.terrains().size()));
        brush.setTerrainIndex(tilemap.terrains().size() - 1);
        return true;
    }

    private boolean renderTerrainRow(SpriteTilemap tilemap, int terrainIndex) {
        ImGui.pushID("terrain" + terrainIndex);
        TerrainDefinition terrain = tilemap.terrains().get(terrainIndex);
        if (ImGui.selectable(terrainIndex + ": " + terrain.name(), terrainIndex == brush.terrainIndex(),
                0, 140.0f, 0.0f)) {
            brush.setTerrainIndex(terrainIndex);
        }
        ImGui.sameLine();
        ImGui.textDisabled(configuredTileCount(tilemap, terrainIndex) + " tiles");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Tiles whose centre is this terrain. Painting does nothing until at least one exists.");
        }
        ImGui.sameLine();
        boolean removed = icons.iconButton("removeTerrain", EditorIcon.REMOVE, BUTTON_SIZE);
        ImGui.popID();
        if (removed) {
            tilemap.removeTerrain(terrainIndex);
            brush.setTerrainIndex(Math.max(0, brush.terrainIndex() - 1));
        }
        return removed;
    }
}
