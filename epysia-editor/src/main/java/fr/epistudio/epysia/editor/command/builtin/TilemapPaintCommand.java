package fr.epistudio.epysia.editor.command.builtin;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.editor.command.CommandContext;
import fr.epistudio.epysia.editor.command.EditorCommand;

import java.util.List;

public final class TilemapPaintCommand implements EditorCommand {

    public record TileEdit(int cellX, int cellY, int beforeTileIndex, int afterTileIndex) {

        public TileEdit inverted() {
            return new TileEdit(cellX, cellY, afterTileIndex, beforeTileIndex);
        }
    }

    private final SpriteTilemap tilemap;
    private final List<TileEdit> edits;
    private final String label;

    public TilemapPaintCommand(SpriteTilemap tilemap, List<TileEdit> edits, String label) {
        this.tilemap = tilemap;
        this.edits = List.copyOf(edits);
        this.label = label;
    }

    @Override
    public void apply(CommandContext context) {
        for (TileEdit edit : edits) {
            tilemap.setTile(edit.cellX(), edit.cellY(), edit.afterTileIndex());
        }
    }

    @Override
    public EditorCommand invert(CommandContext context) {
        return new TilemapPaintCommand(tilemap, edits.stream().map(TileEdit::inverted).toList(), label);
    }

    @Override
    public String label() {
        return label;
    }
}
