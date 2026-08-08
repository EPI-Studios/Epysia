package fr.epistudio.epysia.render.backend;

public record TextureDescriptor(
        int width,
        int height,
        TextureFormat format,
        TextureUsage usage,
        SamplerFilter samplerFilter,
        TextureKind kind,
        int mipLevels,
        int layers,
        TextureWrap wrap,
        int anisotropy
) {
    public static final int NO_ANISOTROPY = 1;

    public TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage,
                             SamplerFilter samplerFilter, TextureKind kind, int mipLevels, int layers,
                             TextureWrap wrap) {
        this(width, height, format, usage, samplerFilter, kind, mipLevels, layers, wrap, NO_ANISOTROPY);
    }

    public TextureDescriptor withAnisotropy(int requested) {
        return new TextureDescriptor(width, height, format, usage, samplerFilter, kind, mipLevels,
                layers, wrap, Math.max(NO_ANISOTROPY, requested));
    }

    public TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage) {
        this(width, height, format, usage, SamplerFilter.LINEAR, TextureKind.TEXTURE_2D, 1, 1, TextureWrap.CLAMP_TO_EDGE);
    }

    public TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage, SamplerFilter samplerFilter) {
        this(width, height, format, usage, samplerFilter, TextureKind.TEXTURE_2D, 1, 1, TextureWrap.CLAMP_TO_EDGE);
    }

    public TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage, TextureWrap wrap) {
        this(width, height, format, usage, SamplerFilter.LINEAR, TextureKind.TEXTURE_2D, 1, 1, wrap);
    }

    public static TextureDescriptor cubemap(int size, TextureFormat format, int mipLevels) {
        return new TextureDescriptor(size, size, format, TextureUsage.SAMPLED, SamplerFilter.LINEAR,
                TextureKind.CUBEMAP, mipLevels, 6, TextureWrap.CLAMP_TO_EDGE);
    }

    public static TextureDescriptor volume(int width, int height, int depth, TextureFormat format,
                                           TextureUsage usage, SamplerFilter samplerFilter, TextureWrap wrap) {
        return new TextureDescriptor(width, height, format, usage, samplerFilter,
                TextureKind.TEXTURE_3D, 1, depth, wrap);
    }

    public int depth() {
        return kind == TextureKind.TEXTURE_3D ? layers : 1;
    }

    public static TextureDescriptor depthArray(int size, int layers, TextureUsage usage) {
        return new TextureDescriptor(size, size, TextureFormat.DEPTH32F, usage, SamplerFilter.LINEAR,
                TextureKind.ARRAY_2D, 1, layers, TextureWrap.CLAMP_TO_EDGE);
    }
}
