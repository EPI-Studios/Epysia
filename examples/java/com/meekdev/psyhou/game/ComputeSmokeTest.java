package com.meekdev.psyhou.game;

import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.ComputeBarrier;
import fr.epistudio.epysia.render.backend.ComputeDispatch;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.scene.Scene;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

public final class ComputeSmokeTest implements RenderSystem {

    private static final int ELEMENT_COUNT = 256;
    private static final int STORAGE_BINDING = 0;
    private static final String COMPUTE_SOURCE = """
            #version 430 core
            layout(local_size_x = 64) in;

            layout(std430, binding = 0) buffer Results {
                uint values[];
            };

            void main() {
                uint index = gl_GlobalInvocationID.x;
                values[index] = index * 2u;
            }
            """;

    private RenderBackend backend;
    private PipelineHandle pipeline;
    private BufferHandle results;
    private BindingSetHandle bindings;
    private boolean verified;

    @Override
    public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
        this.backend = renderBackend;
        BindingSetLayout layout = new BindingSetLayout(List.of(
                new BindingSlot(STORAGE_BINDING, BindingType.STORAGE_BUFFER)));
        pipeline = backend.createComputePipeline(new ComputePipelineDescriptor(COMPUTE_SOURCE, layout));
        results = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(ELEMENT_COUNT * Integer.BYTES)));
        bindings = backend.createBindingSet(new BindingSetDescriptor(layout, List.of(
                new Binding(STORAGE_BINDING,
                        StorageBufferBinding.whole(results, (long) ELEMENT_COUNT * Integer.BYTES)))));
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        if (verified) {
            return;
        }
        backend.dispatchCompute(ComputeDispatch.of(pipeline, bindings, ELEMENT_COUNT / 64));
        backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        ByteBuffer readback = BufferUtils.createByteBuffer(ELEMENT_COUNT * Integer.BYTES);
        backend.readBuffer(results, readback, 0L);
        int sample = readback.asIntBuffer().get(64);
        System.out.println("[compute] values[64] = " + sample + " (expected 128)");
        verified = true;
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        renderBackend.destroy(bindings);
        renderBackend.destroy(results);
        renderBackend.destroy(pipeline);
    }
}
