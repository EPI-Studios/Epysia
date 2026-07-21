package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageResize;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class ThumbnailCache {

    private static final int MAX_ENTRIES = 512;
    private static final int LOADS_PER_FRAME = 4;
    private static final int MAX_THUMBNAIL_SIZE = 128;

    private final OpenGlRenderBackend backend;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> failedPaths = new HashSet<>();
    private int loadBudget;

    public ThumbnailCache(OpenGlRenderBackend backend) {
        this.backend = backend;
    }

    public void beginFrame() {
        loadBudget = LOADS_PER_FRAME;
    }

    public OptionalInt get(String absolutePath) {
        if (failedPaths.contains(absolutePath)) {
            return OptionalInt.empty();
        }
        Entry existing = cache.get(absolutePath);
        if (existing != null) {
            return OptionalInt.of(existing.glTextureName());
        }
        if (loadBudget <= 0) {
            return OptionalInt.empty();
        }
        loadBudget--;
        Optional<Entry> loaded = tryLoad(absolutePath);
        if (loaded.isEmpty()) {
            failedPaths.add(absolutePath);
            return OptionalInt.empty();
        }
        putBounded(absolutePath, loaded.get());
        return OptionalInt.of(loaded.get().glTextureName());
    }

    private void putBounded(String key, Entry entry) {
        cache.put(key, entry);
        if (cache.size() <= MAX_ENTRIES) {
            return;
        }
        Iterator<Map.Entry<String, Entry>> iterator = cache.entrySet().iterator();
        Map.Entry<String, Entry> eldest = iterator.next();
        iterator.remove();
        backend.destroy(eldest.getValue().handle());
    }

    public void shutdown() {
        for (Entry entry : cache.values()) {
            backend.destroy(entry.handle());
        }
        cache.clear();
        failedPaths.clear();
    }

    private Optional<Entry> tryLoad(String absolutePath) {
        Path path = Path.of(absolutePath);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return readFileToDirectBuffer(path).flatMap(this::decodeAndUpload);
    }

    private Optional<Entry> decodeAndUpload(ByteBuffer encoded) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            IntBuffer channelsBuffer = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, widthBuffer, heightBuffer, channelsBuffer, 4);
            if (pixels == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(uploadScaled(pixels, widthBuffer.get(0), heightBuffer.get(0)));
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private Entry uploadScaled(ByteBuffer pixels, int width, int height) {
        if (width <= MAX_THUMBNAIL_SIZE && height <= MAX_THUMBNAIL_SIZE) {
            return uploadPixels(pixels, width, height);
        }
        int targetWidth = scaledDimension(width, width, height);
        int targetHeight = scaledDimension(height, width, height);
        ByteBuffer resized = BufferUtils.createByteBuffer(targetWidth * targetHeight * 4);
        STBImageResize.stbir_resize_uint8_srgb(pixels, width, height, 0,
                resized, targetWidth, targetHeight, 0, STBImageResize.STBIR_RGBA);
        return uploadPixels(resized, targetWidth, targetHeight);
    }

    private static int scaledDimension(int dimension, int width, int height) {
        int longestSide = Math.max(width, height);
        int scaled = Math.round(dimension * (float) MAX_THUMBNAIL_SIZE / longestSide);
        return Math.max(1, scaled);
    }

    private Entry uploadPixels(ByteBuffer pixels, int width, int height) {
        TextureHandle handle = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        backend.writeTexture(handle, pixels);
        return new Entry(handle, backend.glTextureName(handle));
    }

    private Optional<ByteBuffer> readFileToDirectBuffer(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return Optional.of(buffer);
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    private record Entry(TextureHandle handle, int glTextureName) {
    }
}
