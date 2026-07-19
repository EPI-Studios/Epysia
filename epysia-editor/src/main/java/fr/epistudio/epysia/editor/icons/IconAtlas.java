package fr.epistudio.epysia.editor.icons;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.Map;

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

public final class IconAtlas {

    private final Map<EditorIcon, Integer> textures = new EnumMap<>(EditorIcon.class);

    public void loadAll() {
        for (EditorIcon icon : EditorIcon.values()) {
            textures.put(icon, uploadIcon(icon));
        }
    }

    public int textureId(EditorIcon icon) {
        Integer id = textures.get(icon);
        if (id == null) {
            throw new IllegalStateException("Icon atlas not loaded for " + icon);
        }
        return id;
    }

    public void dispose() {
        for (Integer id : textures.values()) {
            glDeleteTextures(id);
        }
        textures.clear();
    }

    private int uploadIcon(EditorIcon icon) {
        ByteBuffer encoded = readResource(icon.resourcePath());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (pixels == null) {
                throw new IllegalStateException("Failed to decode icon " + icon.resourcePath());
            }
            int texture = uploadPixels(pixels, width.get(0), height.get(0));
            STBImage.stbi_image_free(pixels);
            return texture;
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

    private static ByteBuffer readResource(String path) {
        try (InputStream stream = IconAtlas.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new UncheckedIOException(new IOException("Missing icon resource " + path));
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
