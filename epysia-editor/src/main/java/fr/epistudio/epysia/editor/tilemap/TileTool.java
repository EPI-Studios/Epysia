package fr.epistudio.epysia.editor.tilemap;

import fr.epistudio.epysia.editor.icons.EditorIcon;

public enum TileTool {

    PAINT("Paint", "Paint Tiles", EditorIcon.EDIT),
    ERASE("Erase", "Erase Tiles", EditorIcon.ERASER),
    LINE("Line", "Draw Tile Line", EditorIcon.LINE),
    RECTANGLE("Rectangle", "Fill Tile Rectangle", EditorIcon.RECTANGLE),
    BUCKET("Bucket", "Bucket Fill Tiles", EditorIcon.BUCKET),
    PICK("Pick", "Pick Tile", EditorIcon.COLOR_PICK),
    SELECT("Select", "Move Tiles", EditorIcon.TOOL_SELECT),
    TERRAIN("Terrain", "Paint Terrain", EditorIcon.TERRAIN_CONNECT);

    private final String label;
    private final String historyLabel;
    private final EditorIcon icon;

    TileTool(String label, String historyLabel, EditorIcon icon) {
        this.label = label;
        this.historyLabel = historyLabel;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public String historyLabel() {
        return historyLabel;
    }

    public EditorIcon icon() {
        return icon;
    }

    public boolean draggable() {
        return this == PAINT || this == ERASE;
    }

    public boolean rectangular() {
        return this == RECTANGLE || this == SELECT;
    }
}
