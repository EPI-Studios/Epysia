package fr.epistudio.epysia.editor.assets;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SpriteOpaqueBounds {

    public record UnitBounds(float minX, float minY, float maxX, float maxY) {

        public boolean fillsCell() {
            return minX <= 0.0f && minY <= 0.0f && maxX >= 1.0f && maxY >= 1.0f;
        }
    }

    private record CacheKey(Path imageFile, long modifiedMillis) {
    }

    private record PixelWindow(int minColumn, int minRow, int maxColumn, int maxRow) {

        int width() {
            return maxColumn - minColumn;
        }

        int height() {
            return maxRow - minRow;
        }
    }

    private static final int OPAQUE_ALPHA_THRESHOLD = 8;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int ALPHA_OFFSET = 3;

    private final Map<CacheKey, Optional<DecodedImage>> cache = new HashMap<>();

    public Optional<UnitBounds> boundsOf(Path imageFile, int columns, int rows, int cellIndex) {
        int safeColumns = Math.max(1, columns);
        int safeRows = Math.max(1, rows);
        if (cellIndex < 0 || cellIndex >= safeColumns * safeRows) {
            return Optional.empty();
        }
        int column = cellIndex % safeColumns;
        int row = cellIndex / safeColumns;
        return boundsOfRegion(imageFile, (float) column / safeColumns, (float) (safeRows - row - 1) / safeRows,
                (float) (column + 1) / safeColumns, (float) (safeRows - row) / safeRows);
    }

    public Optional<UnitBounds> boundsOfRegion(Path imageFile, float minU, float minV, float maxU, float maxV) {
        return imageOf(imageFile).flatMap(image -> image.scanRegion(minU, minV, maxU, maxV));
    }

    public void invalidate(Path imageFile) {
        cache.keySet().removeIf(key -> key.imageFile().equals(imageFile));
    }

    private Optional<DecodedImage> imageOf(Path imageFile) {
        CacheKey key = new CacheKey(imageFile, modifiedMillisOf(imageFile));
        return cache.computeIfAbsent(key, entry -> decode(entry.imageFile()));
    }

    private static Optional<DecodedImage> decode(Path imageFile) {
        Optional<ByteBuffer> encoded = readFile(imageFile);
        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded.get(), width, height, channels, BYTES_PER_PIXEL);
            if (pixels == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(DecodedImage.copyOf(pixels, width.get(0), height.get(0)));
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private record DecodedImage(byte[] pixels, int width, int height) {

        static DecodedImage copyOf(ByteBuffer source, int width, int height) {
            byte[] copy = new byte[width * height * BYTES_PER_PIXEL];
            source.get(0, copy);
            return new DecodedImage(copy, width, height);
        }

        Optional<UnitBounds> scanRegion(float minU, float minV, float maxU, float maxV) {
            PixelWindow window = windowOf(minU, minV, maxU, maxV);
            if (window.width() <= 0 || window.height() <= 0) {
                return Optional.empty();
            }
            return scanWindow(window);
        }

        private PixelWindow windowOf(float minU, float minV, float maxU, float maxV) {
            int minColumn = Math.clamp(Math.round(Math.min(minU, maxU) * width), 0, width);
            int maxColumn = Math.clamp(Math.round(Math.max(minU, maxU) * width), 0, width);
            int minRow = Math.clamp(Math.round((1.0f - Math.max(minV, maxV)) * height), 0, height);
            int maxRow = Math.clamp(Math.round((1.0f - Math.min(minV, maxV)) * height), 0, height);
            return new PixelWindow(minColumn, minRow, maxColumn, maxRow);
        }

        private Optional<UnitBounds> scanWindow(PixelWindow window) {
            int leftMost = window.width();
            int topMost = window.height();
            int rightMost = -1;
            int bottomMost = -1;
            for (int row = 0; row < window.height(); row++) {
                for (int column = 0; column < window.width(); column++) {
                    if (!opaqueAt(window.minColumn() + column, window.minRow() + row)) {
                        continue;
                    }
                    leftMost = Math.min(leftMost, column);
                    rightMost = Math.max(rightMost, column);
                    topMost = Math.min(topMost, row);
                    bottomMost = Math.max(bottomMost, row);
                }
            }
            return rightMost < 0 ? Optional.empty()
                    : Optional.of(toUnitBounds(leftMost, topMost, rightMost, bottomMost, window));
        }

        private static UnitBounds toUnitBounds(int leftMost, int topMost, int rightMost, int bottomMost,
                                               PixelWindow window) {
            return new UnitBounds((float) leftMost / window.width(),
                    1.0f - (float) (bottomMost + 1) / window.height(),
                    (float) (rightMost + 1) / window.width(),
                    1.0f - (float) topMost / window.height());
        }

        private boolean opaqueAt(int pixelX, int pixelY) {
            int index = (pixelY * width + pixelX) * BYTES_PER_PIXEL + ALPHA_OFFSET;
            return index < pixels.length && (pixels[index] & 0xFF) > OPAQUE_ALPHA_THRESHOLD;
        }
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
