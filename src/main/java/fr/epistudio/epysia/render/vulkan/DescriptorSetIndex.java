package fr.epistudio.epysia.render.vulkan;

import fr.epistudio.epysia.render.backend.BindingType;

public enum DescriptorSetIndex {
    UNIFORM_BUFFER(0),
    STORAGE_BUFFER(1),
    SAMPLED_TEXTURE(2),
    STORAGE_IMAGE(3);

    public static final int COUNT = 4;

    private final int setNumber;

    DescriptorSetIndex(int setNumber) {
        this.setNumber = setNumber;
    }

    public int setNumber() {
        return setNumber;
    }

    public static DescriptorSetIndex of(BindingType type) {
        return switch (type) {
            case UNIFORM_BUFFER -> UNIFORM_BUFFER;
            case STORAGE_BUFFER -> STORAGE_BUFFER;
            case SAMPLED_TEXTURE_2D, SAMPLED_TEXTURE_3D, SAMPLED_TEXTURE_CUBE, SAMPLED_TEXTURE_ARRAY ->
                    SAMPLED_TEXTURE;
            case STORAGE_IMAGE -> STORAGE_IMAGE;
        };
    }
}
