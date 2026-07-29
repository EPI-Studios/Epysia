package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;

import java.util.List;

public final class GpuInstanceCuller {

    public static final int SOURCE_INSTANCES_BINDING = 0;
    public static final int VISIBLE_INSTANCES_BINDING = 1;
    public static final int INDIRECT_ARGUMENTS_BINDING = 2;
    public static final int VISIBILITY_STATE_BINDING = 3;
    public static final int PYRAMID_BINDING = 4;
    public static final int PARAMETERS_BINDING = 5;

    public static final int PHASE_REDRAW_LAST_VISIBLE = 0;
    public static final int PHASE_TEST_REMAINDER = 1;

    public static final int PARAMETERS_BYTES = 64 + 6 * 16 + 16 + 16 + 16 + 16;
    public static final int INDIRECT_ARGUMENT_BYTES = 20;
    private static final int WORKGROUP_SIZE = 64;

    private final PipelineHandle pipeline;

    public GpuInstanceCuller(RenderBackend backend, ShaderLoader shaderLoader) {
        this.pipeline = backend.createComputePipeline(new ComputePipelineDescriptor(
                shaderLoader.load("lib/mesh_cull.comp.glsl").source(), layout()));
    }

    public static BindingSetLayout layout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(SOURCE_INSTANCES_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VISIBLE_INSTANCES_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(INDIRECT_ARGUMENTS_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VISIBILITY_STATE_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(PYRAMID_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(PARAMETERS_BINDING, BindingType.UNIFORM_BUFFER)));
    }

    public PipelineHandle pipeline() {
        return pipeline;
    }

    public static int groupCountFor(int instances) {
        return (instances + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
    }
}
