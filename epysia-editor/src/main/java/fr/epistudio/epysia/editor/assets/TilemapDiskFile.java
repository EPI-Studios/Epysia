package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.AssetLocator;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemap;
import fr.epistudio.epysia.assets.epytilemap.SpriteTilemapJsonCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class TilemapDiskFile {

    private static final SpriteTilemapJsonCodec CODEC = new SpriteTilemapJsonCodec();

    private TilemapDiskFile() {
    }

    public static String serialize(SpriteTilemap tilemap, AssetLocator locator) {
        return CODEC.write(copyWithAtlasPath(tilemap, projectAtlasPath(tilemap.atlasPath(), locator)));
    }

    public static void write(SpriteTilemap tilemap, Path tilemapFile, AssetLocator locator) throws IOException {
        Files.writeString(tilemapFile, serialize(tilemap, locator));
    }

    public static boolean matchesDisk(SpriteTilemap tilemap, Path tilemapFile, AssetLocator locator) {
        try {
            return Files.readString(tilemapFile).equals(serialize(tilemap, locator));
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

    private static String projectAtlasPath(String atlasPath, AssetLocator locator) {
        if (atlasPath.isEmpty()) {
            return atlasPath;
        }
        String stripped = LegacyAssetReferences.stripPrefixes(atlasPath);
        try {
            Path candidate = Path.of(stripped);
            return candidate.isAbsolute() ? locator.fromFile(candidate).toString() : atlasPath;
        } catch (InvalidPathException malformed) {
            return atlasPath;
        }
    }
}
