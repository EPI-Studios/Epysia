package fr.epistudio.epysia.editor.assets;

import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ImagePreviewTexture {

    public record PreviewImage(int textureId, int width, int height) {
    }

    private final OpenGlRenderBackend backend;
    private String cachedPath = "";
    private long cachedModifiedMillis;
    private Optional<TextureHandle> cachedHandle = Optional.empty();
    private Optional<PreviewImage> cachedImage = Optional.empty();

    public ImagePreviewTexture(OpenGlRenderBackend backend) {
        this.backend = backend;
    }

    public Optional<PreviewImage> get(Path imageFile) {
        String path = imageFile.toAbsolutePath().toString();
        long modifiedMillis = modifiedMillisOf(imageFile);
        if (path.equals(cachedPath) && modifiedMillis == cachedModifiedMillis) {
            return cachedImage;
        }
        disposeCurrent();
        cachedPath = path;
        cachedModifiedMillis = modifiedMillis;
        cachedImage = load(imageFile);
        return cachedImage;
    }

    private Optional<PreviewImage> load(Path imageFile) {
        Optional<ByteBuffer> encoded = readFile(imageFile);
        if (encoded.isEmpty()) {
            return Optional.empty();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded.get(), width, height, channels, 4);
            if (pixels == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(upload(pixels, width.get(0), height.get(0)));
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }
    }

    private PreviewImage upload(ByteBuffer pixels, int width, int height) {
        TextureHandle handle = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.NEAREST));
        backend.writeTexture(handle, pixels);
        cachedHandle = Optional.of(handle);
        return new PreviewImage(backend.glTextureName(handle), width, height);
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

    private void disposeCurrent() {
        cachedHandle.ifPresent(backend::destroy);
        cachedHandle = Optional.empty();
        cachedImage = Optional.empty();
    }

    public void dispose() {
        disposeCurrent();
        cachedPath = "";
    }
}
