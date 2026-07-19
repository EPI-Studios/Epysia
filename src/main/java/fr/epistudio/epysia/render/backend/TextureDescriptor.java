package fr.epistudio.epysia.render.backend;

public record TextureDescriptor(
        int width,
        int height,
        TextureFormat format,
        TextureUsage usage,
        SamplerFilter samplerFilter,
        TextureKind kind,
        int mipLevels,
        int layers
) {

    public TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage) {
        this(width, height, format, usage, SamplerFilter.LINEAR, TextureKind.TEXTURE_2D, 1, 1);
    }

    public TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage, SamplerFilter samplerFilter) {
        this(width, height, format, usage, samplerFilter, TextureKind.TEXTURE_2D, 1, 1);
    }

    public static TextureDescriptor cubemap(int size, TextureFormat format, int mipLevels) {
        return new TextureDescriptor(size, size, format, TextureUsage.SAMPLED, SamplerFilter.LINEAR,
                TextureKind.CUBEMAP, mipLevels, 6);
    }

    public static TextureDescriptor depthArray(int size, int layers, TextureUsage usage) {
        return new TextureDescriptor(size, size, TextureFormat.DEPTH32F, usage, SamplerFilter.LINEAR,
                TextureKind.ARRAY_2D, 1, layers);
    }
}
