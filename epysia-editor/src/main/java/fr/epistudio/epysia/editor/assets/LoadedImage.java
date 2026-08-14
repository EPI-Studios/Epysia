package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.editor.icons.GlTextures;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public record LoadedImage(ByteBuffer pixels, int width, int height, int textureId) {

    private static final int CHANNELS = 4;

    public static Optional<LoadedImage> load(Path imageFile) {
        Optional<ByteBuffer> encoded = read(imageFile);
        return encoded.isEmpty() ? Optional.empty() : decode(encoded.get());
    }

    public void dispose() {
        GlTextures.delete(textureId);
    }

    public int sample(int x, int y, int channel) {
        int clampedX = Math.clamp(x, 0, width - 1);
        int clampedY = Math.clamp(y, 0, height - 1);
        return pixels.get((clampedY * width + clampedX) * CHANNELS + channel) & 0xFF;
    }

    private static Optional<LoadedImage> decode(ByteBuffer encoded) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer decoded = STBImage.stbi_load_from_memory(encoded, width, height, channels, CHANNELS);
            if (decoded == null) {
                return Optional.empty();
            }
            LoadedImage image = copyOf(decoded, width.get(0), height.get(0));
            STBImage.stbi_image_free(decoded);
            return Optional.of(image);
        }
    }

    private static LoadedImage copyOf(ByteBuffer decoded, int width, int height) {
        ByteBuffer owned = ByteBuffer.allocateDirect(width * height * CHANNELS);
        owned.put(decoded.duplicate()).flip();
        return new LoadedImage(owned, width, height, GlTextures.upload(owned, width, height));
    }

    private static Optional<ByteBuffer> read(Path imageFile) {
        try {
            byte[] bytes = Files.readAllBytes(imageFile);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            return Optional.of(buffer.put(bytes).flip());
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }
}
