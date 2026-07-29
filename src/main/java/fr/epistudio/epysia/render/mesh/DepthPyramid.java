package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.ComputeBarrier;
import fr.epistudio.epysia.render.backend.ComputeDispatch;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.StorageImageAccess;
import fr.epistudio.epysia.render.backend.StorageImageBinding;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureKind;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.TextureWrap;
import fr.epistudio.epysia.render.shader.ShaderLoader;

import java.util.ArrayList;
import java.util.List;

public final class DepthPyramid {

    private static final int SOURCE_BINDING = 0;
    private static final int TARGET_BINDING = 1;
    private static final int WORKGROUP_SIZE = 8;

    private final RenderBackend backend;
    private final PipelineHandle copyPipeline;
    private final PipelineHandle reducePipeline;
    private final List<BindingSetHandle> levelBindings = new ArrayList<>();

    private TextureHandle pyramid;
    private int width;
    private int height;
    private int levels;

    public DepthPyramid(RenderBackend backend, ShaderLoader shaderLoader) {
        this.backend = backend;
        this.copyPipeline = backend.createComputePipeline(new ComputePipelineDescriptor(
                shaderLoader.load("lib/hiz_copy.comp.glsl").source(), copyLayout()));
        this.reducePipeline = backend.createComputePipeline(new ComputePipelineDescriptor(
                shaderLoader.load("lib/hiz_reduce.comp.glsl").source(), reduceLayout()));
    }

    private static BindingSetLayout copyLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(SOURCE_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(TARGET_BINDING, BindingType.STORAGE_IMAGE)));
    }

    private static BindingSetLayout reduceLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(SOURCE_BINDING, BindingType.STORAGE_IMAGE),
                new BindingSlot(TARGET_BINDING, BindingType.STORAGE_IMAGE)));
    }

    public TextureHandle texture() {
        return pyramid;
    }

    public int levels() {
        return levels;
    }

    public void invalidate() {
        pyramid = null;
    }

    public void resize(TextureHandle sceneDepth, int targetWidth, int targetHeight) {
        if (pyramid != null && targetWidth == width && targetHeight == height) {
            return;
        }
        width = Math.max(1, targetWidth);
        height = Math.max(1, targetHeight);
        levels = levelCountFor(width, height);
        pyramid = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.R32F,
                TextureUsage.SAMPLED, SamplerFilter.NEAREST, TextureKind.TEXTURE_2D, levels, 1,
                TextureWrap.CLAMP_TO_EDGE));
        rebuildBindings(sceneDepth);
    }

    private static int levelCountFor(int width, int height) {
        int levels = 1;
        int extent = Math.max(width, height);
        while (extent > 1) {
            extent >>= 1;
            levels++;
        }
        return levels;
    }

    private void rebuildBindings(TextureHandle sceneDepth) {
        levelBindings.clear();
        levelBindings.add(backend.createBindingSet(new BindingSetDescriptor(copyLayout(), List.of(
                new Binding(SOURCE_BINDING, new SampledTextureBinding(sceneDepth)),
                new Binding(TARGET_BINDING,
                        new StorageImageBinding(pyramid, 0, StorageImageAccess.WRITE_ONLY))))));
        for (int level = 1; level < levels; level++) {
            levelBindings.add(backend.createBindingSet(new BindingSetDescriptor(reduceLayout(), List.of(
                    new Binding(SOURCE_BINDING,
                            new StorageImageBinding(pyramid, level - 1, StorageImageAccess.READ_ONLY)),
                    new Binding(TARGET_BINDING,
                            new StorageImageBinding(pyramid, level, StorageImageAccess.WRITE_ONLY))))));
        }
    }

    public void build() {
        if (pyramid == null) {
            return;
        }
        dispatchLevel(copyPipeline, levelBindings.get(0), width, height);
        for (int level = 1; level < levels; level++) {
            backend.computeBarrier(ComputeBarrier.STORAGE_IMAGE);
            dispatchLevel(reducePipeline, levelBindings.get(level),
                    levelExtent(width, level), levelExtent(height, level));
        }
        backend.computeBarrier(ComputeBarrier.STORAGE_IMAGE);
    }

    private void dispatchLevel(PipelineHandle pipeline, BindingSetHandle bindings,
                               int levelWidth, int levelHeight) {
        backend.dispatchCompute(new ComputeDispatch(pipeline, bindings,
                groupCount(levelWidth), groupCount(levelHeight), 1));
    }

    private static int levelExtent(int base, int level) {
        return Math.max(1, base >> level);
    }

    private static int groupCount(int extent) {
        return (extent + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
    }
}
