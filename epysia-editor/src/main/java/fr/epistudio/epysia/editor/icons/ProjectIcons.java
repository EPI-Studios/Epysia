package fr.epistudio.epysia.editor.icons;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;

public final class ProjectIcons {

    public static final List<String> CANDIDATE_FILENAMES = List.of("icon.png", ".epysia/icon.png");

    private static final int MISSING = 0;
    private static final int CHANNELS = 4;

    private final Map<String, Integer> textures = new HashMap<>();

    public Optional<Integer> of(Path projectRoot) {
        String key = projectRoot.toAbsolutePath().toString();
        Integer known = textures.get(key);
        if (known != null) {
            return known == MISSING ? Optional.empty() : Optional.of(known);
        }
        int loaded = iconFileIn(projectRoot).map(ProjectIcons::upload).orElse(MISSING);
        textures.put(key, loaded);
        return loaded == MISSING ? Optional.empty() : Optional.of(loaded);
    }

    public void forget(Path projectRoot) {
        Integer texture = textures.remove(projectRoot.toAbsolutePath().toString());
        if (texture != null && texture != MISSING) {
            glDeleteTextures(texture);
        }
    }

    public void dispose() {
        for (Integer texture : textures.values()) {
            if (texture != MISSING) {
                glDeleteTextures(texture);
            }
        }
        textures.clear();
    }

    private static Optional<Path> iconFileIn(Path projectRoot) {
        for (String candidate : CANDIDATE_FILENAMES) {
            Path file = projectRoot.resolve(candidate);
            if (Files.isRegularFile(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    private static int upload(Path imageFile) {
        Optional<ByteBuffer> encoded = read(imageFile);
        if (encoded.isEmpty()) {
            return MISSING;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded.get(), width, height, channels, CHANNELS);
            if (pixels == null) {
                return MISSING;
            }
            int texture = uploadPixels(pixels, width.get(0), height.get(0));
            STBImage.stbi_image_free(pixels);
            return texture;
        }
    }

    private static Optional<ByteBuffer> read(Path imageFile) {
        try {
            byte[] bytes = Files.readAllBytes(imageFile);
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.put(bytes).flip();
            return Optional.of(buffer);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static int uploadPixels(ByteBuffer pixels, int width, int height) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);
        return texture;
    }
}
