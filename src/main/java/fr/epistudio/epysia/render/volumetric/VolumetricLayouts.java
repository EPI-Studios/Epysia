package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferHandle;

import java.util.List;

record VolumetricLayouts(BufferHandle shapeBuffer, long shapeBytes) {
    static BindingSetLayout occupancyLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(DensityVolumeResources.UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(DensityVolumeResources.OCCUPANCY_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(DensityVolumeResources.SHAPE_BINDING, BindingType.STORAGE_BUFFER)));
    }

    static BindingSetLayout densityLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(DensityVolumeResources.UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(DensityVolumeResources.OCCUPANCY_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(DensityVolumeResources.DENSITY_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(DensityVolumeResources.PING_BINDING, BindingType.STORAGE_BUFFER)));
    }

    static BindingSetLayout raymarchLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(DensityVolumeResources.UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(DensityVolumeResources.DENSITY_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(DensityVolumeResources.NOISE_BINDING, BindingType.SAMPLED_TEXTURE_3D),
                new BindingSlot(DensityVolumeResources.SCENE_DEPTH_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(DensityVolumeResources.DEFORMER_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(DensityVolumeResources.SCATTERED_IMAGE_BINDING, BindingType.STORAGE_IMAGE),
                new BindingSlot(DensityVolumeResources.TRANSMITTANCE_IMAGE_BINDING, BindingType.STORAGE_IMAGE)));
    }

    static BindingSetLayout compositeLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(2, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(3, BindingType.UNIFORM_BUFFER)));
    }

    static BindingSetLayout noiseLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.STORAGE_IMAGE)));
    }
}
