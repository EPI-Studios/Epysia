package fr.epistudio.epysia.window;

import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.lwjgl.glfw.GLFW.glfwSetWindowIcon;

public final class WindowIcon {

    private static final String RESOURCE_PREFIX = "/branding/epysia-icon-";
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private record Decoded(int width, int height, ByteBuffer pixels) {
    }

    private WindowIcon() {
    }

    public static void applyDefault(long handle) {
        List<Decoded> decoded = decodeAll();
        if (decoded.isEmpty()) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(decoded.size(), stack);
            for (int index = 0; index < decoded.size(); index++) {
                Decoded image = decoded.get(index);
                images.position(index).width(image.width()).height(image.height()).pixels(image.pixels());
            }
            images.position(0);
            glfwSetWindowIcon(handle, images);
        } finally {
            decoded.forEach(image -> STBImage.stbi_image_free(image.pixels()));
        }
    }

    private static List<Decoded> decodeAll() {
        List<Decoded> decoded = new ArrayList<>();
        for (int size : SIZES) {
            decode(RESOURCE_PREFIX + size + ".png").ifPresent(decoded::add);
        }
        return decoded;
    }

    private static Optional<Decoded> decode(String resource) {
        Optional<ByteBuffer> encoded = readResource(resource);
        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded.get(), width, height, channels, 4);
            MemoryUtil.memFree(encoded.get());
            return pixels == null
                    ? Optional.empty()
                    : Optional.of(new Decoded(width.get(0), height.get(0), pixels));
        }
    }

    private static Optional<ByteBuffer> readResource(String resource) {
        try (InputStream stream = WindowIcon.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return Optional.empty();
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return Optional.of(buffer);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }
}
