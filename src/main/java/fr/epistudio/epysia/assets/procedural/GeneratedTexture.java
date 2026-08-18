package fr.epistudio.epysia.assets.procedural;

import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.TextureWrap;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public final class GeneratedTexture {

    public static final int CHANNELS = 4;
    private static final float COLOR_MAXIMUM = 255.0f;

    private GeneratedTexture() {
    }

    public static ByteBuffer surface(int width, int height) {
        return BufferUtils.createByteBuffer(width * height * CHANNELS);
    }

    public static void write(ByteBuffer surface, int index, float red, float green, float blue, float alpha) {
        int base = index * CHANNELS;
        surface.put(base, toByte(red));
        surface.put(base + 1, toByte(green));
        surface.put(base + 2, toByte(blue));
        surface.put(base + 3, toByte(alpha));
    }

    public static TextureHandle upload(RenderBackend backend, int width, int height,
                                       ByteBuffer pixels, TextureWrap wrap, SamplerFilter filter) {
        TextureHandle handle = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, filter,
                fr.epistudio.epysia.render.backend.TextureKind.TEXTURE_2D, 1, 1, wrap));
        pixels.position(0);
        pixels.limit(pixels.capacity());
        backend.writeTexture(handle, pixels);
        return handle;
    }

    private static byte toByte(float value) {
        return (byte) Math.round(Math.clamp(value, 0.0f, 1.0f) * COLOR_MAXIMUM);
    }
}
