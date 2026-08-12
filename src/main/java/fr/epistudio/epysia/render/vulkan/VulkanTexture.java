package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureKind;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class VulkanTexture {

    private final long image;
    private final long allocation;
    private final long defaultView;
    private final long sampler;
    private final TextureFormat format;
    private final TextureKind kind;
    private final int vulkanFormat;
    private final int aspectMask;
    private final int width;
    private final int height;
    private final int layers;
    private final int mipLevels;
    private final Map<AttachmentViewKey, Long> attachmentViews = new HashMap<>();

    private final int[] layerLayouts;

    public VulkanTexture(long image, long allocation, long defaultView, long sampler,
                         TextureFormat format, TextureKind kind, int vulkanFormat, int aspectMask,
                         int width, int height, int layers, int mipLevels, int initialLayout) {
        this.image = image;
        this.allocation = allocation;
        this.defaultView = defaultView;
        this.sampler = sampler;
        this.format = format;
        this.kind = kind;
        this.vulkanFormat = vulkanFormat;
        this.aspectMask = aspectMask;
        this.width = width;
        this.height = height;
        this.layers = layers;
        this.mipLevels = mipLevels;
        this.layerLayouts = new int[switch (kind) {
            case CUBEMAP -> 6;
            case ARRAY_2D -> Math.max(1, layers);
            case TEXTURE_2D, TEXTURE_3D -> 1;
        }];
        Arrays.fill(this.layerLayouts, initialLayout);
    }

    public long image() {
        return image;
    }

    public long allocation() {
        return allocation;
    }

    public long defaultView() {
        return defaultView;
    }

    public long sampler() {
        return sampler;
    }

    public TextureFormat format() {
        return format;
    }

    public TextureKind kind() {
        return kind;
    }

    public int vulkanFormat() {
        return vulkanFormat;
    }

    public int aspectMask() {
        return aspectMask;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int layers() {
        return layers;
    }

    public int mipLevels() {
        return mipLevels;
    }

    public int layerCount() {
        return layerLayouts.length;
    }

    public int currentLayout() {
        return layerLayouts[0];
    }

    public int currentLayout(int layer) {
        return layerLayouts[Math.floorMod(layer, layerLayouts.length)];
    }

    public boolean allLayersAt(int layout) {
        for (int current : layerLayouts) {
            if (current != layout) {
                return false;
            }
        }
        return true;
    }

    public void recordLayout(int layout) {
        Arrays.fill(layerLayouts, layout);
    }

    public void recordLayout(int layer, int layout) {
        layerLayouts[Math.floorMod(layer, layerLayouts.length)] = layout;
    }

    public boolean isDepth() {
        return format == TextureFormat.DEPTH32F || format == TextureFormat.DEPTH32F_STENCIL8;
    }

    public Map<AttachmentViewKey, Long> attachmentViews() {
        return attachmentViews;
    }

    public record AttachmentViewKey(int mipLevel, int layer) {
    }
}
