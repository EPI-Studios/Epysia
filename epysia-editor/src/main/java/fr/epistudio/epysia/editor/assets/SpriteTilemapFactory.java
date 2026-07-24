package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemapJsonCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SpriteTilemapFactory {

    public static final String EXTENSION = ".epytilemap";

    private static final int DEFAULT_WIDTH_CELLS = 32;
    private static final int DEFAULT_HEIGHT_CELLS = 16;
    private static final float DEFAULT_CELL_SIZE = 1.0f;

    private SpriteTilemapFactory() {
    }

    public static Path createFor(Path atlasPath) throws IOException {
        Path tilemapFile = uniqueSiblingFor(atlasPath);
        SpriteTilemap tilemap = new SpriteTilemap(DEFAULT_WIDTH_CELLS, DEFAULT_HEIGHT_CELLS,
                DEFAULT_CELL_SIZE, DEFAULT_CELL_SIZE, atlasPath.getFileName().toString());
        Files.writeString(tilemapFile, new SpriteTilemapJsonCodec().write(tilemap));
        return tilemapFile;
    }

    private static Path uniqueSiblingFor(Path atlasPath) {
        String fileName = atlasPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path candidate = atlasPath.resolveSibling(base + EXTENSION);
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = atlasPath.resolveSibling(base + " " + index + EXTENSION);
            index++;
        }
        return candidate;
    }
}
