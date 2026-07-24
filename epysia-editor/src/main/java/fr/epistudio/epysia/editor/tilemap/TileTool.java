package fr.epistudio.epysia.editor.tilemap;

import fr.epistudio.epysia.editor.icons.EditorIcon;

public enum TileTool {

    PAINT("Paint", "Paint Tiles", "B", "Drag to lay tiles. Right drag always erases.", EditorIcon.EDIT),
    ERASE("Erase", "Erase Tiles", "E", "Drag to clear cells on the active layer.", EditorIcon.ERASER),
    LINE("Line", "Draw Tile Line", "L", "Press at the start, release at the end.", EditorIcon.LINE),
    RECTANGLE("Rectangle", "Fill Tile Rectangle", "R", "Drag a box and it fills on release.", EditorIcon.RECTANGLE),
    BUCKET("Bucket", "Bucket Fill Tiles", "G", "Click to flood the connected cells sharing that tile.",
            EditorIcon.BUCKET),
    PICK("Pick", "Pick Tile", "I", "Click a cell to make its tile the active brush.", EditorIcon.COLOR_PICK),
    SELECT("Select", "Move Tiles", "S", "Drag a box, then C copies, V pastes, Delete clears.",
            EditorIcon.TOOL_SELECT),
    TERRAIN("Terrain", "Paint Terrain", "T", "Drag a shape and matching edges and corners resolve on release.",
            EditorIcon.TERRAIN_CONNECT);

    private final String label;
    private final String historyLabel;
    private final String shortcut;
    private final String hint;
    private final EditorIcon icon;

    TileTool(String label, String historyLabel, String shortcut, String hint, EditorIcon icon) {
        this.label = label;
        this.historyLabel = historyLabel;
        this.shortcut = shortcut;
        this.hint = hint;
        this.icon = icon;
    }

    public String label() {
        return label;
    }

    public String historyLabel() {
        return historyLabel;
    }

    public String shortcut() {
        return shortcut;
    }

    public String hint() {
        return hint;
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
