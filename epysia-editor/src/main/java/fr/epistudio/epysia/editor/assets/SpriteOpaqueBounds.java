package fr.epistudio.epysia.editor.assets;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SpriteOpaqueBounds {

    public record UnitBounds(float minX, float minY, float maxX, float maxY) {

        public boolean fillsCell() {
            return minX <= 0.0f && minY <= 0.0f && maxX >= 1.0f && maxY >= 1.0f;
        }
    }

    private record CacheKey(Path imageFile, long modifiedMillis, int columns, int rows) {
    }

    private static final int OPAQUE_ALPHA_THRESHOLD = 8;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int ALPHA_OFFSET = 3;

    private final Map<CacheKey, List<Optional<UnitBounds>>> cache = new HashMap<>();

    public Optional<UnitBounds> boundsOf(Path imageFile, int columns, int rows, int cellIndex) {
        List<Optional<UnitBounds>> cells = cellsOf(imageFile, columns, rows);
        if (cellIndex < 0 || cellIndex >= cells.size()) {
            return Optional.empty();
        }
        return cells.get(cellIndex);
    }

    public int cellCount(Path imageFile, int columns, int rows) {
        return cellsOf(imageFile, columns, rows).size();
    }

    public void invalidate(Path imageFile) {
        cache.keySet().removeIf(key -> key.imageFile().equals(imageFile));
    }

    private List<Optional<UnitBounds>> cellsOf(Path imageFile, int columns, int rows) {
        CacheKey key = new CacheKey(imageFile, modifiedMillisOf(imageFile),
                Math.max(1, columns), Math.max(1, rows));
        return cache.computeIfAbsent(key, SpriteOpaqueBounds::decode);
    }

    private static List<Optional<UnitBounds>> decode(CacheKey key) {
        Optional<ByteBuffer> encoded = readFile(key.imageFile());
        if (encoded.isEmpty()) {
            return List.of();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded.get(), width, height, channels, BYTES_PER_PIXEL);
            if (pixels == null) {
                return List.of();
            }
            try {
                return scanCells(pixels, width.get(0), height.get(0), key);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private static List<Optional<UnitBounds>> scanCells(ByteBuffer pixels, int imageWidth, int imageHeight,
                                                        CacheKey key) {
        int cellWidth = imageWidth / key.columns();
        int cellHeight = imageHeight / key.rows();
        if (cellWidth <= 0 || cellHeight <= 0) {
            return List.of();
        }
        List<Optional<UnitBounds>> cells = new ArrayList<>();
        for (int row = 0; row < key.rows(); row++) {
            for (int column = 0; column < key.columns(); column++) {
                cells.add(scanCell(pixels, imageWidth, column * cellWidth, row * cellHeight, cellWidth, cellHeight));
            }
        }
        return List.copyOf(cells);
    }

    private static Optional<UnitBounds> scanCell(ByteBuffer pixels, int imageWidth,
                                                 int originX, int originY, int cellWidth, int cellHeight) {
        int minColumn = cellWidth;
        int minRow = cellHeight;
        int maxColumn = -1;
        int maxRow = -1;
        for (int row = 0; row < cellHeight; row++) {
            for (int column = 0; column < cellWidth; column++) {
                if (!opaqueAt(pixels, imageWidth, originX + column, originY + row)) {
                    continue;
                }
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
            }
        }
        return maxColumn < 0 ? Optional.empty()
                : Optional.of(toUnitBounds(minColumn, minRow, maxColumn, maxRow, cellWidth, cellHeight));
    }

    private static UnitBounds toUnitBounds(int minColumn, int minRow, int maxColumn, int maxRow,
                                           int cellWidth, int cellHeight) {
        float minX = (float) minColumn / cellWidth;
        float maxX = (float) (maxColumn + 1) / cellWidth;
        float minY = 1.0f - (float) (maxRow + 1) / cellHeight;
        float maxY = 1.0f - (float) minRow / cellHeight;
        return new UnitBounds(minX, minY, maxX, maxY);
    }

    private static boolean opaqueAt(ByteBuffer pixels, int imageWidth, int pixelX, int pixelY) {
        int index = (pixelY * imageWidth + pixelX) * BYTES_PER_PIXEL + ALPHA_OFFSET;
        return index < pixels.capacity() && (pixels.get(index) & 0xFF) > OPAQUE_ALPHA_THRESHOLD;
    }

    private static Optional<ByteBuffer> readFile(Path imageFile) {
        try {
            byte[] bytes = Files.readAllBytes(imageFile);
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return Optional.of(buffer);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static long modifiedMillisOf(Path imageFile) {
        try {
            return Files.getLastModifiedTime(imageFile).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }
}
