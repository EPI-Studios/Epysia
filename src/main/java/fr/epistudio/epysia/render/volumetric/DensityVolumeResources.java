package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.StorageImageBinding;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

final class DensityVolumeResources {
    static final int VOLUME_UBO_BYTES = 576;
    static final int COMPOSITE_UBO_BYTES = 16;
    static final int UBO_BINDING = 0;
    static final int OCCUPANCY_BINDING = 2;
    static final int SHAPE_BINDING = 3;
    static final int DENSITY_BINDING = 4;
    static final int PING_BINDING = 5;
    static final int NOISE_BINDING = 6;
    static final int SCENE_DEPTH_BINDING = 7;
    static final int DEFORMER_BINDING = 8;
    static final int SCATTERED_IMAGE_BINDING = 2;
    static final int TRANSMITTANCE_IMAGE_BINDING = 3;

    private final RenderBackend backend;
    private final VolumetricLayouts layouts;
    private final int voxelCount;
    private final BufferHandle volumeUbo;
    private final BufferHandle compositeUbo;
    private final BufferHandle occupancyBuffer;
    private final BufferHandle densityBuffer;
    private final BufferHandle pingBuffer;
    private final BufferHandle deformerBuffer;

    private TextureHandle scatteredTexture;
    private TextureHandle transmittanceTexture;
    private BindingSetHandle occupancyBindings;
    private BindingSetHandle densityBindings;
    private BindingSetHandle raymarchBindings;
    private BindingSetHandle compositeBindings;
    private int targetWidth;
    private int targetHeight;

    DensityVolumeResources(RenderBackend backend, VolumetricLayouts layouts, int voxelCount) {
        this.backend = backend;
        this.layouts = layouts;
        this.voxelCount = voxelCount;
        this.volumeUbo = uniformBuffer(VOLUME_UBO_BYTES);
        this.compositeUbo = uniformBuffer(COMPOSITE_UBO_BYTES);
        this.occupancyBuffer = storageBuffer(voxelCount * Integer.BYTES);
        this.densityBuffer = storageBuffer(voxelCount * Integer.BYTES);
        this.pingBuffer = storageBuffer(voxelCount * Integer.BYTES);
        this.deformerBuffer = storageBuffer(DensityDeformer.HARD_LIMIT
                * DensityDeformer.FLOATS_PER_ENTRY * Float.BYTES);
        createComputeBindings();
    }

    private BufferHandle uniformBuffer(int byteSize) {
        return backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(byteSize), true));
    }

    private BufferHandle storageBuffer(int byteSize) {
        return backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(byteSize)));
    }

    private void createComputeBindings() {
        occupancyBindings = backend.createBindingSet(new BindingSetDescriptor(layouts.occupancyLayout(), List.of(
                new Binding(UBO_BINDING, UniformBufferBinding.whole(volumeUbo, VOLUME_UBO_BYTES)),
                new Binding(OCCUPANCY_BINDING, StorageBufferBinding.whole(occupancyBuffer, storageBytes())),
                new Binding(SHAPE_BINDING, StorageBufferBinding.whole(layouts.shapeBuffer(), layouts.shapeBytes())))));
        densityBindings = backend.createBindingSet(new BindingSetDescriptor(layouts.densityLayout(), List.of(
                new Binding(UBO_BINDING, UniformBufferBinding.whole(volumeUbo, VOLUME_UBO_BYTES)),
                new Binding(OCCUPANCY_BINDING, StorageBufferBinding.whole(occupancyBuffer, storageBytes())),
                new Binding(DENSITY_BINDING, StorageBufferBinding.whole(densityBuffer, storageBytes())),
                new Binding(PING_BINDING, StorageBufferBinding.whole(pingBuffer, storageBytes())))));
    }

    private long storageBytes() {
        return (long) voxelCount * Integer.BYTES;
    }

    void resizeTargets(int width, int height, TextureHandle noiseVolume, TextureHandle sceneDepth) {
        destroyTargets();
        targetWidth = Math.max(1, width);
        targetHeight = Math.max(1, height);
        scatteredTexture = backend.createTexture(new TextureDescriptor(targetWidth, targetHeight,
                TextureFormat.RGBA16F, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        transmittanceTexture = backend.createTexture(new TextureDescriptor(targetWidth, targetHeight,
                TextureFormat.R16F, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        createFrameBindings(noiseVolume, sceneDepth);
    }

    private void createFrameBindings(TextureHandle noiseVolume, TextureHandle sceneDepth) {
        raymarchBindings = backend.createBindingSet(new BindingSetDescriptor(layouts.raymarchLayout(), List.of(
                new Binding(UBO_BINDING, UniformBufferBinding.whole(volumeUbo, VOLUME_UBO_BYTES)),
                new Binding(DENSITY_BINDING, StorageBufferBinding.whole(densityBuffer, storageBytes())),
                new Binding(NOISE_BINDING, new SampledTextureBinding(noiseVolume)),
                new Binding(SCENE_DEPTH_BINDING, new SampledTextureBinding(sceneDepth)),
                new Binding(DEFORMER_BINDING, StorageBufferBinding.whole(deformerBuffer, deformerBytes())),
                new Binding(SCATTERED_IMAGE_BINDING, StorageImageBinding.writeOnly(scatteredTexture)),
                new Binding(TRANSMITTANCE_IMAGE_BINDING, StorageImageBinding.writeOnly(transmittanceTexture)))));
        compositeBindings = backend.createBindingSet(new BindingSetDescriptor(layouts.compositeLayout(), List.of(
                new Binding(0, new SampledTextureBinding(scatteredTexture)),
                new Binding(1, new SampledTextureBinding(transmittanceTexture)),
                new Binding(2, new SampledTextureBinding(sceneDepth)),
                new Binding(3, UniformBufferBinding.whole(compositeUbo, COMPOSITE_UBO_BYTES)))));
    }

    private long deformerBytes() {
        return (long) DensityDeformer.HARD_LIMIT * DensityDeformer.FLOATS_PER_ENTRY * Float.BYTES;
    }

    void writeVolumeUniforms(ByteBuffer contents) {
        backend.writeBuffer(volumeUbo, contents, 0L);
    }

    void writeCompositeUniforms(ByteBuffer contents) {
        backend.writeBuffer(compositeUbo, contents, 0L);
    }

    void writeDeformers(ByteBuffer contents) {
        backend.writeBuffer(deformerBuffer, contents, 0L);
    }

    boolean matchesTargetSize(int width, int height) {
        return targetWidth == width && targetHeight == height;
    }

    int voxelCount() {
        return voxelCount;
    }

    int targetWidth() {
        return targetWidth;
    }

    int targetHeight() {
        return targetHeight;
    }

    BindingSetHandle occupancyBindings() {
        return occupancyBindings;
    }

    BindingSetHandle densityBindings() {
        return densityBindings;
    }

    BindingSetHandle raymarchBindings() {
        return raymarchBindings;
    }

    BindingSetHandle compositeBindings() {
        return compositeBindings;
    }

    private void destroyTargets() {
        if (scatteredTexture == null) {
            return;
        }
        backend.destroy(raymarchBindings);
        backend.destroy(compositeBindings);
        backend.destroy(scatteredTexture);
        backend.destroy(transmittanceTexture);
        scatteredTexture = null;
    }

    void destroy() {
        destroyTargets();
        backend.destroy(occupancyBindings);
        backend.destroy(densityBindings);
        backend.destroy(volumeUbo);
        backend.destroy(compositeUbo);
        backend.destroy(occupancyBuffer);
        backend.destroy(densityBuffer);
        backend.destroy(pingBuffer);
        backend.destroy(deformerBuffer);
    }
}
