package fr.epistudio.epysia.render.text;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Font {

    public static final int ATLAS_SIZE = 512;
    public static final int FIRST_CHAR = 32;
    public static final int CHAR_COUNT = 96;

    private static final String RESOURCE_ROOT = "src/main/resources";

    private final float pixelHeight;
    private final TextureHandle atlasTexture;
    private final STBTTBakedChar.Buffer characterData;

    private Font(float pixelHeight, TextureHandle atlasTexture, STBTTBakedChar.Buffer characterData) {
        this.pixelHeight = pixelHeight;
        this.atlasTexture = atlasTexture;
        this.characterData = characterData;
    }

    public static Font loadFromResource(RenderBackend backend, String relativePath, float pixelHeight) {
        return loadFromResource(backend, relativePath, pixelHeight, SamplerFilter.LINEAR);
    }

    public static Font loadFromResource(RenderBackend backend, String relativePath, float pixelHeight, SamplerFilter samplerFilter) {
        return bake(backend, readResource(relativePath), pixelHeight, samplerFilter);
    }

    public static Font bake(RenderBackend backend, ByteBuffer ttfData, float pixelHeight) {
        return bake(backend, ttfData, pixelHeight, SamplerFilter.LINEAR);
    }

    public static Font bake(RenderBackend backend, ByteBuffer ttfData, float pixelHeight, SamplerFilter samplerFilter) {
        ByteBuffer grayscale = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE);
        STBTTBakedChar.Buffer characterData = STBTTBakedChar.malloc(CHAR_COUNT);
        int result = STBTruetype.stbtt_BakeFontBitmap(ttfData, pixelHeight, grayscale, ATLAS_SIZE, ATLAS_SIZE, FIRST_CHAR, characterData);
        if (result <= 0) {
            characterData.free();
            throw new EpysiaException("Failed to bake font bitmap; atlas too small or font invalid.");
        }
        ByteBuffer rgba = expandToRgba(grayscale);
        TextureHandle handle = backend.createTexture(new TextureDescriptor(ATLAS_SIZE, ATLAS_SIZE, TextureFormat.RGBA8, TextureUsage.SAMPLED, samplerFilter));
        backend.writeTexture(handle, rgba);
        return new Font(pixelHeight, handle, characterData);
    }

    private static ByteBuffer expandToRgba(ByteBuffer grayscale) {
        ByteBuffer rgba = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
        for (int i = 0; i < ATLAS_SIZE * ATLAS_SIZE; i++) {
            byte coverage = grayscale.get(i);
            rgba.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put(coverage);
        }
        rgba.flip();
        return rgba;
    }

    public TextureHandle atlasTexture() {
        return atlasTexture;
    }

    public float pixelHeight() {
        return pixelHeight;
    }

    public float measureWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        float advance = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.charAt(i);
            if (codePoint < FIRST_CHAR || codePoint >= FIRST_CHAR + CHAR_COUNT) {
                continue;
            }
            advance += characterData.get(codePoint - FIRST_CHAR).xadvance();
        }
        return advance;
    }

    public STBTTBakedChar.Buffer characterData() {
        return characterData;
    }

    public void destroy(RenderBackend backend) {
        backend.destroy(atlasTexture);
        characterData.free();
    }

    private static ByteBuffer readResource(String relativePath) {
        Path absolute = Path.of(RESOURCE_ROOT).resolve(relativePath);
        if (Files.isRegularFile(absolute)) {
            return readFromFilesystem(absolute);
        }
        return readFromClasspath(relativePath);
    }

    private static ByteBuffer readFromFilesystem(Path absolute) {
        try {
            byte[] bytes = Files.readAllBytes(absolute);
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read font " + absolute + ": " + exception.getMessage());
        }
    }

    private static ByteBuffer readFromClasspath(String relativePath) {
        try (InputStream stream = Font.class.getClassLoader().getResourceAsStream(relativePath)) {
            if (stream == null) {
                throw new EpysiaException("Font resource not found: " + relativePath);
            }
            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read font " + relativePath + ": " + exception.getMessage());
        }
    }
}
