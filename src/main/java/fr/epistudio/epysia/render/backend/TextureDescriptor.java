package fr.epistudio.epysia.render.backend;

public record TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage, SamplerFilter samplerFilter) {

    public TextureDescriptor(int width, int height, TextureFormat format, TextureUsage usage) {
        this(width, height, format, usage, SamplerFilter.LINEAR);
    }
}
