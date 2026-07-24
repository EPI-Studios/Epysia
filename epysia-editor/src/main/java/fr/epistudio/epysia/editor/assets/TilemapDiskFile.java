package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemapJsonCodec;
import fr.epistudio.epysia.assets.loaders.TexturePathPrefixes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TilemapDiskFile {

    private static final SpriteTilemapJsonCodec CODEC = new SpriteTilemapJsonCodec();

    private TilemapDiskFile() {
    }

    public static String serialize(SpriteTilemap tilemap, Path tilemapFile) {
        return CODEC.write(copyWithAtlasPath(tilemap, relativizedAtlasPath(tilemap.atlasPath(), tilemapFile)));
    }

    public static void write(SpriteTilemap tilemap, Path tilemapFile) throws IOException {
        Files.writeString(tilemapFile, serialize(tilemap, tilemapFile));
    }

    public static boolean matchesDisk(SpriteTilemap tilemap, Path tilemapFile) {
        try {
            return Files.readString(tilemapFile).equals(serialize(tilemap, tilemapFile));
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static SpriteTilemap copyWithAtlasPath(SpriteTilemap source, String atlasPath) {
        SpriteTilemap copy = new SpriteTilemap(source.width(), source.height(),
                source.cellWidth(), source.cellHeight(), atlasPath);
        for (int cellY = 0; cellY < source.height(); cellY++) {
            for (int cellX = 0; cellX < source.width(); cellX++) {
                copy.setTile(cellX, cellY, source.tileIndex(cellX, cellY));
            }
        }
        source.solidTiles().forEach(tileIndex -> copy.setSolid(tileIndex, true));
        return copy;
    }

    private static String relativizedAtlasPath(String atlasPath, Path tilemapFile) {
        if (atlasPath.isEmpty()) {
            return atlasPath;
        }
        String stripped = TexturePathPrefixes.stripPrefixes(atlasPath);
        String prefix = atlasPath.substring(0, atlasPath.length() - stripped.length());
        Path absolute = Path.of(stripped);
        if (!absolute.isAbsolute()) {
            return atlasPath;
        }
        return relativeOrOriginal(atlasPath, prefix, absolute, tilemapFile);
    }

    private static String relativeOrOriginal(String atlasPath, String prefix, Path absolute, Path tilemapFile) {
        try {
            Path base = tilemapFile.toAbsolutePath().getParent();
            return prefix + base.relativize(absolute.normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException unrelated) {
            return atlasPath;
        }
    }
}
