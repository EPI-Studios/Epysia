package fr.epistudio.epysia.render.texture;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public final class DrawableTexture {

    private static final int CHANNELS = 4;
    private static final float COLOR_MAXIMUM = 255.0f;

    private final TextureHandle handle;
    private final ByteBuffer pixels;
    private final int width;
    private final int height;
    private boolean dirty = true;

    private DrawableTexture(TextureHandle handle, ByteBuffer pixels, int width, int height) {
        this.handle = handle;
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    public static DrawableTexture create(EngineServices services, int width, int height) {
        return create(services.renderBackend(), width, height);
    }

    public static DrawableTexture create(RenderBackend backend, int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        TextureHandle handle = backend.createTexture(new TextureDescriptor(safeWidth, safeHeight,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        ByteBuffer pixels = BufferUtils.createByteBuffer(safeWidth * safeHeight * CHANNELS);
        return new DrawableTexture(handle, pixels, safeWidth, safeHeight);
    }

    public TextureHandle texture() {
        return handle;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public DrawableTexture clear(float red, float green, float blue, float alpha) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writePixel(x, y, red, green, blue, alpha);
            }
        }
        dirty = true;
        return this;
    }

    public DrawableTexture pixel(int x, int y, float red, float green, float blue, float alpha) {
        if (isOutside(x, y)) {
            return this;
        }
        writePixel(x, y, red, green, blue, alpha);
        dirty = true;
        return this;
    }

    public DrawableTexture rect(int left, int top, int rectWidth, int rectHeight,
                                float red, float green, float blue, float alpha) {
        for (int y = top; y < top + rectHeight; y++) {
            for (int x = left; x < left + rectWidth; x++) {
                pixel(x, y, red, green, blue, alpha);
            }
        }
        return this;
    }

    public DrawableTexture line(int startX, int startY, int endX, int endY,
                                float red, float green, float blue, float alpha) {
        int steps = Math.max(Math.abs(endX - startX), Math.abs(endY - startY));
        if (steps == 0) {
            return pixel(startX, startY, red, green, blue, alpha);
        }
        for (int step = 0; step <= steps; step++) {
            float progress = (float) step / steps;
            pixel(Math.round(startX + (endX - startX) * progress),
                    Math.round(startY + (endY - startY) * progress), red, green, blue, alpha);
        }
        return this;
    }

    public DrawableTexture circle(int centerX, int centerY, int radius,
                                  float red, float green, float blue, float alpha) {
        int squaredRadius = radius * radius;
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                int deltaX = x - centerX;
                int deltaY = y - centerY;
                if (deltaX * deltaX + deltaY * deltaY <= squaredRadius) {
                    pixel(x, y, red, green, blue, alpha);
                }
            }
        }
        return this;
    }

    public DrawableTexture blit(DrawableTexture source, int destinationX, int destinationY) {
        for (int y = 0; y < source.height; y++) {
            for (int x = 0; x < source.width; x++) {
                int base = source.offsetOf(x, y);
                pixel(destinationX + x, destinationY + y,
                        channelAt(source.pixels, base), channelAt(source.pixels, base + 1),
                        channelAt(source.pixels, base + 2), channelAt(source.pixels, base + 3));
            }
        }
        return this;
    }

    public void apply(EngineServices services) {
        apply(services.renderBackend());
    }

    public void apply(RenderBackend backend) {
        if (!dirty) {
            return;
        }
        pixels.position(0);
        pixels.limit(pixels.capacity());
        backend.writeTexture(handle, pixels);
        dirty = false;
    }

    public void destroy(EngineServices services) {
        services.renderBackend().destroy(handle);
    }

    private boolean isOutside(int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height;
    }

    private int offsetOf(int x, int y) {
        return (y * width + x) * CHANNELS;
    }

    private void writePixel(int x, int y, float red, float green, float blue, float alpha) {
        int base = offsetOf(x, y);
        pixels.put(base, toByte(red));
        pixels.put(base + 1, toByte(green));
        pixels.put(base + 2, toByte(blue));
        pixels.put(base + 3, toByte(alpha));
    }

    private static byte toByte(float value) {
        return (byte) Math.round(Math.clamp(value, 0.0f, 1.0f) * COLOR_MAXIMUM);
    }

    private static float channelAt(ByteBuffer buffer, int index) {
        return (buffer.get(index) & 0xFF) / COLOR_MAXIMUM;
    }
}
