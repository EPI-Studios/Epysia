package fr.epistudio.epysia.render.texture;

import fr.epistudio.epysia.assets.AssetMetaFile;
import fr.epistudio.epysia.assets.AssetVariant;
import fr.epistudio.epysia.assets.loaders.TextureImportSettings;
import fr.epistudio.epysia.assets.source.AssetResolvers;
import fr.epistudio.epysia.assets.source.AssetSource;
import fr.epistudio.epysia.assets.source.FilesystemAssetSource;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.scene.serialization.JsonReader;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureKind;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.TextureWrap;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.util.Random;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

public final class Texture2D {

    private Texture2D() {
    }

    public static TextureHandle loadFrom(RenderBackend backend, AssetSource source, TextureFormat format) {
        return loadFrom(backend, source, format, TextureWrap.REPEAT);
    }

    public static TextureHandle loadFrom(RenderBackend backend, AssetSource source, TextureFormat format, TextureWrap wrap) {
        return loadFrom(backend, source, format, wrap, SamplerFilter.LINEAR);
    }

    public static TextureHandle loadFrom(RenderBackend backend, AssetSource source, TextureFormat format,
            TextureWrap wrap, SamplerFilter filter) {
        return decodeAndUpload(backend, copyToDirectBuffer(readBytes(source)), format, wrap, filter);
    }

    public static TextureHandle load(RenderBackend backend, String path) {
        return load(backend, path, TextureFormat.RGBA8);
    }

    public static TextureHandle load(RenderBackend backend, String path, TextureFormat format) {
        return load(backend, path, format, TextureWrap.REPEAT);
    }

    public static TextureHandle load(RenderBackend backend, String path, TextureFormat format, TextureWrap wrap) {
        return load(backend, path, format, wrap, importSettings(path).filter());
    }

    public static TextureHandle load(RenderBackend backend, String path, TextureFormat format, TextureWrap wrap,
            SamplerFilter filter) {
        AssetSource source = AssetResolvers.forPath(path, "").source()
                .orElseThrow(() -> new EpysiaException("Texture resource not found: " + path));
        if (HighDynamicRangeImage.isHighDynamicRange(path)) {
            return uploadHighDynamicRange(backend, decodeHighDynamicRange(path, source), wrap, filter);
        }
        return loadFrom(backend, source, format, wrap, filter);
    }

    private static HighDynamicRangeImage decodeHighDynamicRange(String path, AssetSource source) {
        if (path.toLowerCase(Locale.ROOT).endsWith(".exr")) {
            return HighDynamicRangeImage.decodeOpenExr(source.path());
        }
        return HighDynamicRangeImage.decodeRadiance(copyToDirectBuffer(readBytes(source)));
    }

    private static TextureHandle uploadHighDynamicRange(RenderBackend backend, HighDynamicRangeImage image,
            TextureWrap wrap, SamplerFilter filter) {
        try {
            TextureHandle handle = backend.createTexture(new TextureDescriptor(image.width(), image.height(),
                    TextureFormat.RGBA16F, TextureUsage.SAMPLED, filter, TextureKind.TEXTURE_2D, 1, 1, wrap));
            backend.writeTexture(handle, image.pixels());
            return handle;
        } finally {
            image.free();
        }
    }

    public static TextureImportSettings importSettings(String path) {
        return TextureImportSettings.from(metaOf(path), AssetVariant.none());
    }

    private static Map<String, Object> metaOf(String path) {
        try {
            Path file = Path.of(path);
            return Files.isRegularFile(file) ? AssetMetaFile.settingsOf(file) : Map.of();
        } catch (InvalidPathException malformed) {
            return Map.of();
        }
    }

    public static TextureHandle loadFromFile(RenderBackend backend, Path imagePath) {
        return loadFromFile(backend, imagePath, TextureFormat.RGBA8);
    }

    public static TextureHandle loadFromFile(RenderBackend backend, Path imagePath, TextureFormat format) {
        return loadFrom(backend, new FilesystemAssetSource(imagePath), format);
    }

    public static TextureHandle loadFromResource(RenderBackend backend, String relativePath) {
        return load(backend, relativePath, TextureFormat.RGBA8);
    }

    public static TextureHandle loadFromResource(RenderBackend backend, String relativePath, TextureFormat format) {
        return load(backend, relativePath, format);
    }

    private static byte[] readBytes(AssetSource source) {
        InputStream stream = source.open().orElseThrow(() ->
                new EpysiaException("Texture resource not found on filesystem or classpath: " + source.path()));
        try (stream) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new EpysiaException("Failed to read texture " + source.path() + ": " + exception.getMessage());
        }
    }

    public static TextureHandle whitePixel(RenderBackend backend) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
        return upload(backend, 1, 1, pixel, TextureFormat.RGBA8);
    }

    public static TextureHandle solidColor(RenderBackend backend, int red, int green, int blue) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) red).put((byte) green).put((byte) blue).put((byte) 0xFF).flip();
        return upload(backend, 1, 1, pixel, TextureFormat.RGBA8);
    }

    public static TextureHandle fromPixels(RenderBackend backend, int width, int height, ByteBuffer rgbaPixels) {
        return upload(backend, width, height, rgbaPixels, TextureFormat.RGBA8);
    }

    public static TextureHandle fromTilingPixels(RenderBackend backend, int width, int height,
                                                 ByteBuffer rgbaPixels, TextureFormat format) {
        int mipLevels = 32 - Integer.numberOfLeadingZeros(Math.max(1, Math.max(width, height)));
        TextureHandle handle = backend.createTexture(new TextureDescriptor(width, height, format,
                TextureUsage.SAMPLED, SamplerFilter.LINEAR, TextureKind.TEXTURE_2D, 1, mipLevels,
                TextureWrap.REPEAT));
        backend.writeTexture(handle, rgbaPixels);
        backend.generateMipmaps(handle);
        return handle;
    }

    public static TextureHandle valueNoise(RenderBackend backend, int size, long seed) {
        Random random = new Random(seed);
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

    private static TextureHandle decodeAndUpload(RenderBackend backend, ByteBuffer encodedBytes, TextureFormat format,
            TextureWrap wrap, SamplerFilter filter) {
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
                return upload(backend, widthBuffer.get(0), heightBuffer.get(0), pixels, format, wrap, filter);
            } finally {
                STBImage.stbi_image_free(pixels);
            }
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
        return upload(backend, width, height, pixels, format, TextureWrap.CLAMP_TO_EDGE);
    }

    private static TextureHandle upload(RenderBackend backend, int width, int height, ByteBuffer pixels,
            TextureFormat format, TextureWrap wrap) {
        return upload(backend, width, height, pixels, format, wrap, SamplerFilter.LINEAR);
    }

    private static TextureHandle upload(RenderBackend backend, int width, int height, ByteBuffer pixels,
            TextureFormat format, TextureWrap wrap, SamplerFilter filter) {
        TextureHandle handle = backend.createTexture(new TextureDescriptor(width, height, format,
                TextureUsage.SAMPLED, filter, TextureKind.TEXTURE_2D, 1, 1, wrap));
        backend.writeTexture(handle, pixels);
        return handle;
    }
}
