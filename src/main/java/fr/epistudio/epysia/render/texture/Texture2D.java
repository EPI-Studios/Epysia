package fr.epistudio.epysia.render.texture;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Texture2D {

    private static final String RESOURCE_ROOT = "src/main/resources";

    private Texture2D() {
    }

    public static TextureHandle loadFromFile(RenderBackend backend, Path imagePath) {
        return loadFromFile(backend, imagePath, TextureFormat.RGBA8);
    }

    public static TextureHandle loadFromFile(RenderBackend backend, Path imagePath, TextureFormat format) {
        return decodeAndUpload(backend, readAllBytesAsBuffer(imagePath), format);
    }

    public static TextureHandle loadFromResource(RenderBackend backend, String relativePath) {
        return loadFromResource(backend, relativePath, TextureFormat.RGBA8);
    }

    public static TextureHandle loadFromResource(RenderBackend backend, String relativePath, TextureFormat format) {
        Path absolute = Path.of(RESOURCE_ROOT).resolve(relativePath);
        if (Files.isRegularFile(absolute)) {
            return loadFromFile(backend, absolute, format);
        }
        try (InputStream stream = Texture2D.class.getClassLoader().getResourceAsStream(relativePath)) {
            if (stream == null) {
                throw new EpysiaException("Texture resource not found on filesystem or classpath: " + relativePath);
            }
            return decodeAndUpload(backend, copyToDirectBuffer(stream.readAllBytes()), format);
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read texture " + relativePath + ": " + exception.getMessage());
        }
    }

    public static TextureHandle whitePixel(RenderBackend backend) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
        return upload(backend, 1, 1, pixel, TextureFormat.RGBA8);
    }

    public static TextureHandle valueNoise(RenderBackend backend, int size, long seed) {
        java.util.Random random = new java.util.Random(seed);
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int i = 0; i < size * size; i++) {
            byte value = (byte) random.nextInt(256);
            pixels.put(value).put(value).put(value).put((byte) 0xFF);
        }
        pixels.flip();
        return upload(backend, size, size, pixels, TextureFormat.RGBA8);
    }

    public static TextureHandle checkerboard(RenderBackend backend, int size, int cellPixels) {
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean dark = ((x / cellPixels) + (y / cellPixels)) % 2 == 0;
                writeColor(pixels, dark);
            }
        }
        pixels.flip();
        return upload(backend, size, size, pixels, TextureFormat.SRGB8_ALPHA8);
    }

    private static TextureHandle decodeAndUpload(RenderBackend backend, ByteBuffer encodedBytes, TextureFormat format) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            IntBuffer channelsBuffer = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(true);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encodedBytes, widthBuffer, heightBuffer, channelsBuffer, 4);
            if (pixels == null) {
                throw new EpysiaException("Image decode failed: " + STBImage.stbi_failure_reason());
            }
            try {
                return upload(backend, widthBuffer.get(0), heightBuffer.get(0), pixels, format);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private static ByteBuffer readAllBytesAsBuffer(Path path) {
        try {
            return copyToDirectBuffer(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read texture " + path + ": " + exception.getMessage());
        }
    }

    private static ByteBuffer copyToDirectBuffer(byte[] data) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(data.length);
        buffer.put(data).flip();
        return buffer;
    }

    private static void writeColor(ByteBuffer destination, boolean dark) {
        if (dark) {
            destination.put((byte) 0x40).put((byte) 0x40).put((byte) 0x48).put((byte) 0xFF);
        } else {
            destination.put((byte) 0xD0).put((byte) 0xC8).put((byte) 0xB0).put((byte) 0xFF);
        }
    }

    private static TextureHandle upload(RenderBackend backend, int width, int height, ByteBuffer pixels, TextureFormat format) {
        TextureHandle handle = backend.createTexture(new TextureDescriptor(width, height, format, TextureUsage.SAMPLED));
        backend.writeTexture(handle, pixels);
        return handle;
    }
}
