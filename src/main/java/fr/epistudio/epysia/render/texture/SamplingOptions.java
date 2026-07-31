package fr.epistudio.epysia.render.texture;

import fr.epistudio.epysia.render.backend.TextureDescriptor;

public record SamplingOptions(boolean mipmaps, int anisotropy) {

    public static SamplingOptions none() {
        return new SamplingOptions(false, TextureDescriptor.NO_ANISOTROPY);
    }

    public static SamplingOptions mipmapped(int anisotropy) {
        return new SamplingOptions(true, Math.max(TextureDescriptor.NO_ANISOTROPY, anisotropy));
    }
}
