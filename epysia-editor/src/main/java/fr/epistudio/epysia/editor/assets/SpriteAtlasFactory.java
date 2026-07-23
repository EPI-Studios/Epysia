package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.assets.epyatlas.SpriteAtlas;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasGrid;
import fr.epistudio.epysia.assets.epyatlas.SpriteAtlasJsonCodec;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SpriteAtlasFactory {

    public static final String EXTENSION = ".epyatlas";

    private static final int FALLBACK_CELL_SIZE = 32;

    private SpriteAtlasFactory() {
    }

    public static Path createGridAtlasFor(Path texturePath) throws IOException {
        Path atlasFile = uniqueSiblingFor(texturePath);
        SpriteAtlasGrid grid = gridFor(texturePath);
        SpriteAtlas atlas = SpriteAtlas.gridAtlas(texturePath.getFileName().toString(), grid, List.of());
        Files.writeString(atlasFile, new SpriteAtlasJsonCodec().write(atlas));
        return atlasFile;
    }

    private static Path uniqueSiblingFor(Path texturePath) {
        String fileName = texturePath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path candidate = texturePath.resolveSibling(base + EXTENSION);
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = texturePath.resolveSibling(base + " " + index + EXTENSION);
            index++;
        }
        return candidate;
    }

    private static SpriteAtlasGrid gridFor(Path texturePath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!STBImage.stbi_info(texturePath.toAbsolutePath().toString(), width, height, channels)) {
                return new SpriteAtlasGrid(FALLBACK_CELL_SIZE, FALLBACK_CELL_SIZE, 1, 1);
            }
            return gridFor(width.get(0), height.get(0));
        }
    }

    private static SpriteAtlasGrid gridFor(int width, int height) {
        int cellSize = Math.max(1, Math.min(width, height));
        int columns = Math.max(1, width / cellSize);
        int rows = Math.max(1, height / cellSize);
        return new SpriteAtlasGrid(cellSize, cellSize, columns, rows);
    }
}
