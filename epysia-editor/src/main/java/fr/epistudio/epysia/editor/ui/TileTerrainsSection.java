package fr.epistudio.epysia.editor.ui;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.TerrainDefinition;
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
        if (!icons.toggleButton("mode" + mode.name(), icon, BUTTON_SIZE, active)) {
            return false;
        }
        tilemap.setTerrainMatchMode(mode);
        return true;
    }

    private boolean renderAddButton(SpriteTilemap tilemap) {
        if (!icons.iconButton("addTerrain", EditorIcon.ADD, BUTTON_SIZE)) {
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
        boolean removed = icons.iconButton("removeTerrain", EditorIcon.REMOVE, BUTTON_SIZE);
        ImGui.popID();
        if (removed) {
            tilemap.removeTerrain(terrainIndex);
            brush.setTerrainIndex(Math.max(0, brush.terrainIndex() - 1));
        }
        return removed;
    }
}
