package fr.epistudio.epysia.vfx;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BlendMode;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.ComputeBarrier;
import fr.epistudio.epysia.render.backend.ComputeDispatch;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DepthTest;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class VfxRenderSystem implements RenderSystem {

    private static final int PARTICLE_BYTES = 96;
    private static final int EFFECT_UBO_BYTES = 32;
    private static final int INDIRECT_BYTES = 20;
    private static final int WORKGROUP_SIZE = 64;
    private static final float MAXIMUM_DELTA_SECONDS = 0.25f;

    private final ShaderLoader shaderLoader;
    private final MeshRenderSystem meshRenderSystem;
    private final Logger logger;
    private final Map<ParticleEffect, EffectResources> effectResources = new IdentityHashMap<>();
    private final ByteBuffer effectUboScratch = BufferUtils.createByteBuffer(EFFECT_UBO_BYTES);
    private final Vector3f emitterPosition = new Vector3f();

    private RenderBackend backend;
    private PipelineHandle resetPipeline;
    private PipelineHandle spawnPipeline;
    private PipelineHandle updatePipeline;
    private PipelineHandle billboardPipeline;
    private BindingSetLayout computeLayout;
    private BindingSetLayout drawLayout;
    private MeshHandle quadMesh;
    private BufferHandle quadVertexBuffer;
    private BufferHandle quadIndexBuffer;
    private long lastFrameNanos;

    public VfxRenderSystem(ShaderLoader shaderLoader, MeshRenderSystem meshRenderSystem, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.meshRenderSystem = meshRenderSystem;
        this.logger = logger;
    }

    @Override
    public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
        this.backend = renderBackend;
        computeLayout = buildComputeLayout();
        drawLayout = buildDrawLayout();
        resetPipeline = createComputePipeline("vfx/particle_reset.comp.glsl");
        spawnPipeline = createComputePipeline("vfx/fountain_spawn.comp.glsl");
        updatePipeline = createComputePipeline("vfx/fountain_update.comp.glsl");
        billboardPipeline = createBillboardPipeline();
        createQuadMesh();
        lastFrameNanos = System.nanoTime();
    }

    private static BindingSetLayout buildComputeLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.STORAGE_BUFFER),
                new BindingSlot(1, BindingType.STORAGE_BUFFER),
                new BindingSlot(2, BindingType.STORAGE_BUFFER),
                new BindingSlot(3, BindingType.STORAGE_BUFFER),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER)));
    }

    private static BindingSetLayout buildDrawLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER),
                new BindingSlot(0, BindingType.STORAGE_BUFFER),
                new BindingSlot(1, BindingType.STORAGE_BUFFER),
                new BindingSlot(2, BindingType.STORAGE_BUFFER),
                new BindingSlot(3, BindingType.STORAGE_BUFFER)));
    }

    private PipelineHandle createComputePipeline(String resourcePath) {
        String source = shaderLoader.load(resourcePath).source();
        return backend.createComputePipeline(new ComputePipelineDescriptor(source, computeLayout));
    }

    private PipelineHandle createBillboardPipeline() {
        ShaderSource source = new ShaderSource(
                shaderLoader.load("vfx/particle_billboard.vert.glsl").source(),
                shaderLoader.load("vfx/particle_billboard.frag.glsl").source());
        VertexLayout vertexLayout = new VertexLayout(
                List.of(new VertexAttribute(0, VertexFormat.FLOAT3, 0)), 12);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.LESS_EQUAL,
                BlendMode.ADDITIVE, CullMode.NONE, false);
        return backend.createPipeline(new PipelineDescriptor(source, vertexLayout, state, drawLayout));
    }

    private void createQuadMesh() {
        ByteBuffer vertices = BufferUtils.createByteBuffer(4 * 12);
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        }
        vertices.flip();
        ByteBuffer indices = BufferUtils.createByteBuffer(6 * Integer.BYTES);
        indices.asIntBuffer().put(new int[]{0, 1, 2, 0, 2, 3});
        quadVertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertices));
        quadIndexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, indices));
        quadMesh = backend.createMesh(new MeshDescriptor(quadVertexBuffer, quadIndexBuffer, 0, 6, IndexFormat.UINT32));
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        float delta = advanceClock();
        List<ParticleEffect> seen = new ArrayList<>();
        for (GameObject gameObject : scene.gameObjects()) {
            ParticleEffect effect = gameObject.getComponentOrNull(ParticleEffect.class);
            Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
            if (effect == null || transform == null) {
                continue;
            }
            seen.add(effect);
            simulateAndSubmit(effect, transform, delta, frame);
        }
        purgeStale(seen);
    }

    private float advanceClock() {
        long now = System.nanoTime();
        float delta = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        return Math.clamp(delta, 0.0f, MAXIMUM_DELTA_SECONDS);
    }

    private void simulateAndSubmit(ParticleEffect effect, Transform3D transform, float delta, FrameBuilder frame) {
        EffectResources resources = effectResources.computeIfAbsent(effect, this::createResources);
        int spawnCount = effect.consumeSpawnCount(delta);
        writeEffectUbo(resources, transform, delta, spawnCount, effect);
        effect.recordSpawned(spawnCount);
        backend.dispatchCompute(ComputeDispatch.of(resetPipeline, resources.computeBindings(), 1));
        backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        if (spawnCount > 0) {
            backend.dispatchCompute(ComputeDispatch.of(spawnPipeline, resources.computeBindings(),
                    groupsFor(spawnCount)));
            backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        }
        backend.dispatchCompute(ComputeDispatch.of(updatePipeline, resources.computeBindings(),
                groupsFor(effect.poolSize())));
        backend.computeBarrier(ComputeBarrier.ALL);
        frame.submit(RenderPasses.TRANSPARENT_3D,
                DrawCommand.indirect(billboardPipeline, quadMesh, resources.drawBindings(),
                        Long.MAX_VALUE, resources.indirectBuffer()));
    }

    private static int groupsFor(int threads) {
        return (threads + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
    }

    private void writeEffectUbo(EffectResources resources, Transform3D transform, float delta,
                                int spawnCount, ParticleEffect effect) {
        Matrix4f world = transform.worldMatrix();
        world.getTranslation(emitterPosition);
        effectUboScratch.clear();
        effectUboScratch.putFloat(emitterPosition.x).putFloat(emitterPosition.y).putFloat(emitterPosition.z);
        effectUboScratch.putFloat(delta);
        effectUboScratch.putInt(spawnCount);
        effectUboScratch.putInt(effect.seed());
        effectUboScratch.putInt((int) effect.totalSpawned());
        effectUboScratch.putInt(effect.poolSize());
        effectUboScratch.flip();
        backend.writeBuffer(resources.effectUbo(), effectUboScratch, 0L);
    }

    private EffectResources createResources(ParticleEffect effect) {
        int poolSize = effect.poolSize();
        BufferHandle pool = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(poolSize * PARTICLE_BYTES)));
        BufferHandle aliveList = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(poolSize * Integer.BYTES)));
        BufferHandle freeList = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, initialFreeList(poolSize)));
        BufferHandle indirect = backend.createBuffer(new BufferDescriptor(BufferUsage.INDIRECT,
                BufferUtils.createByteBuffer(INDIRECT_BYTES)));
        BufferHandle effectUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(EFFECT_UBO_BYTES)));
        return new EffectResources(pool, aliveList, freeList, indirect, effectUbo,
                createComputeBindings(pool, aliveList, freeList, indirect, effectUbo),
                createDrawBindings(pool, aliveList, freeList, indirect, effectUbo), poolSize);
    }

    private static ByteBuffer initialFreeList(int poolSize) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(4 + poolSize * Integer.BYTES);
        buffer.putInt(poolSize);
        for (int slot = 0; slot < poolSize; slot++) {
            buffer.putInt(slot);
        }
        buffer.flip();
        return buffer;
    }

    private BindingSetHandle createComputeBindings(BufferHandle pool, BufferHandle aliveList,
            BufferHandle freeList, BufferHandle indirect, BufferHandle effectUbo) {
        return backend.createBindingSet(new BindingSetDescriptor(computeLayout, List.of(
                new Binding(0, StorageBufferBinding.whole(pool, 0L)),
                new Binding(1, StorageBufferBinding.whole(aliveList, 0L)),
                new Binding(2, StorageBufferBinding.whole(freeList, 0L)),
                new Binding(3, StorageBufferBinding.whole(indirect, 0L)),
                new Binding(1, UniformBufferBinding.whole(effectUbo, EFFECT_UBO_BYTES)))));
    }

    private BindingSetHandle createDrawBindings(BufferHandle pool, BufferHandle aliveList,
            BufferHandle freeList, BufferHandle indirect, BufferHandle effectUbo) {
        return backend.createBindingSet(new BindingSetDescriptor(drawLayout, List.of(
                new Binding(0, UniformBufferBinding.whole(meshRenderSystem.frameUniformBuffer(),
                        fr.epistudio.epysia.render.mesh.MeshShaderBindings.FRAME_UBO_SIZE)),
                new Binding(1, UniformBufferBinding.whole(effectUbo, EFFECT_UBO_BYTES)),
                new Binding(0, StorageBufferBinding.whole(pool, 0L)),
                new Binding(1, StorageBufferBinding.whole(aliveList, 0L)),
                new Binding(2, StorageBufferBinding.whole(freeList, 0L)),
                new Binding(3, StorageBufferBinding.whole(indirect, 0L)))));
    }

    private void purgeStale(List<ParticleEffect> seen) {
        Iterator<Map.Entry<ParticleEffect, EffectResources>> iterator = effectResources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ParticleEffect, EffectResources> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                destroyResources(entry.getValue());
                iterator.remove();
            }
        }
    }

    private void destroyResources(EffectResources resources) {
        backend.destroy(resources.computeBindings());
        backend.destroy(resources.drawBindings());
        backend.destroy(resources.pool());
        backend.destroy(resources.aliveList());
        backend.destroy(resources.freeList());
        backend.destroy(resources.indirectBuffer());
        backend.destroy(resources.effectUbo());
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        for (EffectResources resources : effectResources.values()) {
            destroyResources(resources);
        }
        effectResources.clear();
        renderBackend.destroy(quadMesh);
        renderBackend.destroy(quadVertexBuffer);
        renderBackend.destroy(quadIndexBuffer);
        renderBackend.destroy(billboardPipeline);
        renderBackend.destroy(resetPipeline);
        renderBackend.destroy(spawnPipeline);
        renderBackend.destroy(updatePipeline);
    }

    private record EffectResources(BufferHandle pool, BufferHandle aliveList, BufferHandle freeList,
                                   BufferHandle indirectBuffer, BufferHandle effectUbo,
                                   BindingSetHandle computeBindings, BindingSetHandle drawBindings,
                                   int poolSize) {
    }
}
