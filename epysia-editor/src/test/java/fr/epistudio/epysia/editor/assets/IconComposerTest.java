package fr.epistudio.epysia.editor.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class IconComposerTest {

    private static final int[] RED = {255, 0, 0, 255};
    private static final int[] GREEN = {0, 255, 0, 255};
    private static final int[] BLUE = {0, 0, 255, 255};
    private static final int[] WHITE = {255, 255, 255, 255};

    @Test
    void theWholeImageComesBackUnchanged(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("icon.png");

        IconComposer.write(quadrants(), new float[]{0.0f, 1.0f, 1.0f, 0.0f},
                new float[]{0.0f, 0.0f, 1.0f, 1.0f}, 2, target);

        assertEquals(named(RED, GREEN, WHITE, BLUE), pixelsOf(target));
    }

    @Test
    void aQuarterTurnMovesEveryQuadrantOneStepAround(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("icon.png");

        IconComposer.write(quadrants(), new float[]{1.0f, 1.0f, 0.0f, 0.0f},
                new float[]{0.0f, 1.0f, 1.0f, 0.0f}, 2, target);

        assertEquals(named(GREEN, BLUE, RED, WHITE), pixelsOf(target));
    }

    @Test
    void aCropOverOneQuadrantKeepsOnlyThatQuadrant(@TempDir Path directory) throws IOException {
        Path target = directory.resolve("icon.png");

        IconComposer.write(quadrants(), new float[]{0.0f, 0.5f, 0.5f, 0.0f},
                new float[]{0.0f, 0.0f, 0.5f, 0.5f}, 1, target);

        assertEquals(named(RED), pixelsOf(target));
    }

    private static LoadedImage quadrants() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(2 * 2 * 4);
        for (int[] color : List.of(RED, GREEN, WHITE, BLUE)) {
            for (int channel : color) {
                pixels.put((byte) channel);
            }
        }
        return new LoadedImage(pixels.flip(), 2, 2, 0);
    }

    private static List<String> named(int[]... colors) {
        List<String> names = new java.util.ArrayList<>();
        for (int[] color : colors) {
            names.add(nameOf(color[0], color[1], color[2], color[3]));
        }
        return names;
    }

    private static String nameOf(int red, int green, int blue, int alpha) {
        return red + "," + green + "," + blue + "," + alpha;
    }

    private static List<String> pixelsOf(Path file) throws IOException {
        ByteBuffer encoded = ByteBuffer.allocateDirect(Math.toIntExact(Files.size(file)));
        encoded.put(Files.readAllBytes(file)).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer decoded = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            List<String> pixels = new java.util.ArrayList<>();
            for (int index = 0; index < width.get(0) * height.get(0); index++) {
                pixels.add(nameOf(decoded.get(index * 4) & 0xFF, decoded.get(index * 4 + 1) & 0xFF,
                        decoded.get(index * 4 + 2) & 0xFF, decoded.get(index * 4 + 3) & 0xFF));
            }
            STBImage.stbi_image_free(decoded);
            return pixels;
        }
    }
}
