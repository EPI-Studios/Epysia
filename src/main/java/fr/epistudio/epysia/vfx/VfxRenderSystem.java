package fr.epistudio.epysia.vfx;

import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.graph.GraphAsset;
import fr.epistudio.epysia.graph.GraphJsonCodec;
import fr.epistudio.epysia.graph.vfx.VfxGraphCompiler;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
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
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.vfx.lut.VfxLutPack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class VfxRenderSystem implements RenderSystem {

    private static final int PARTICLE_BYTES = VfxEffectResources.PARTICLE_BYTES;
    private static final int EFFECT_UBO_BYTES = VfxEffectResources.EFFECT_UBO_BYTES;
    private static final int INDIRECT_BYTES = VfxEffectResources.INDIRECT_BYTES;
    private static final int INDIRECT_INSTANCE_COUNT_OFFSET = 4;
    private static final int WORKGROUP_SIZE = 64;
    private static final float MAXIMUM_DELTA_SECONDS = 0.25f;

    private final ShaderLoader shaderLoader;
    private final MeshRenderSystem meshRenderSystem;
    private final Logger logger;
    private final Map<ParticleEffect, VfxEffectResources> effectResources = new IdentityHashMap<>();
    private final Map<ParticleEffect, CompiledGraphPipelines> compiledGraphs = new IdentityHashMap<>();
    private final ByteBuffer effectUboScratch = BufferUtils.createByteBuffer(EFFECT_UBO_BYTES);
    private final Vector3f emitterPosition = new Vector3f();
    private final Set<ParticleEffect> warnedMissingTransform =
            Collections.newSetFromMap(new IdentityHashMap<>());

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
    private float timeScale = 1.0f;

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
                new BindingSlot(VfxEffectResources.POOL_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.ALIVE_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.FREE_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.INDIRECT_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.CURVE_LUT_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.GRADIENT_LUT_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER)));
    }

    private static BindingSetLayout buildDrawLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER),
                new BindingSlot(VfxEffectResources.POOL_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.ALIVE_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.FREE_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.INDIRECT_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.CURVE_LUT_BINDING, BindingType.STORAGE_BUFFER),
                new BindingSlot(VfxEffectResources.GRADIENT_LUT_BINDING, BindingType.STORAGE_BUFFER)));
    }

    private PipelineHandle createComputePipeline(String resourcePath) {
        String source = shaderLoader.load(resourcePath).source();
        return backend.createComputePipeline(new ComputePipelineDescriptor(source, computeLayout));
    }

    private PipelineHandle createBillboardPipeline() {
        ShaderSource source = new ShaderSource(
                shaderLoader.load("vfx/particle_billboard.vert.glsl").source(),
                shaderLoader.load("vfx/particle_billboard.frag.glsl").source());
        return backend.createPipeline(new PipelineDescriptor(source, quadVertexLayout(),
                billboardState(), drawLayout));
    }

    private static VertexLayout quadVertexLayout() {
        return new VertexLayout(List.of(new VertexAttribute(0, VertexFormat.FLOAT3, 0)), 12);
    }

    private static RenderState billboardState() {
        return new RenderState(Topology.TRIANGLES, DepthTest.LESS_EQUAL,
                BlendMode.ADDITIVE, CullMode.NONE, false);
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
            if (effect == null) {
                continue;
            }
            Transform3D transform = gameObject.getComponentOrNull(Transform3D.class);
            if (transform == null) {
                warnMissingTransformOnce(gameObject, effect);
                continue;
            }
            seen.add(effect);
            simulateAndSubmit(effect, transform, delta, frame);
        }
        purgeStale(seen);
    }

    private void warnMissingTransformOnce(GameObject gameObject, ParticleEffect effect) {
        if (warnedMissingTransform.add(effect)) {
            logger.warn("[VfxRenderSystem] Particle Effect on '" + gameObject.name()
                    + "' needs a Transform3D and will not simulate without one.");
        }
    }

    private float advanceClock() {
        long now = System.nanoTime();
        float delta = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        return Math.clamp(delta * timeScale, 0.0f, MAXIMUM_DELTA_SECONDS);
    }

    public void setTimeScale(float scale) {
        timeScale = Math.max(0.0f, scale);
    }

    public String debugSnapshot(ParticleEffect effect) {
        VfxEffectResources resources = effectResources.get(effect);
        if (backend == null || resources == null) {
            return "no resources";
        }
        ByteBuffer indirect = BufferUtils.createByteBuffer(INDIRECT_BYTES);
        backend.readBuffer(resources.indirectBuffer(), indirect, 0L);
        ByteBuffer freeTop = BufferUtils.createByteBuffer(Integer.BYTES);
        backend.readBuffer(resources.freeList(), freeTop, 0L);
        ByteBuffer firstParticle = BufferUtils.createByteBuffer(PARTICLE_BYTES);
        backend.readBuffer(resources.pool(), firstParticle, 0L);
        return "indexCount=" + indirect.getInt(0) + " instanceCount=" + indirect.getInt(4)
                + " freeTop=" + freeTop.getInt(0) + "/" + resources.poolSize()
                + " particle0.age=" + firstParticle.getFloat(12)
                + " particle0.lifetime=" + firstParticle.getFloat(28)
                + " particle0.y=" + firstParticle.getFloat(4);
    }

    public OptionalInt aliveCountOf(ParticleEffect effect) {
        VfxEffectResources resources = effectResources.get(effect);
        if (backend == null || resources == null) {
            return OptionalInt.empty();
        }
        ByteBuffer readback = BufferUtils.createByteBuffer(Integer.BYTES);
        backend.readBuffer(resources.indirectBuffer(), readback, INDIRECT_INSTANCE_COUNT_OFFSET);
        return OptionalInt.of(readback.getInt(0));
    }

    private void simulateAndSubmit(ParticleEffect effect, Transform3D transform, float delta, FrameBuilder frame) {
        VfxEffectResources resources = effectResources.computeIfAbsent(effect, this::createResources);
        Optional<CompiledGraphPipelines> compiled = resolveGraphPipelines(effect);
        if (!effect.graphPath().isEmpty() && compiled.isEmpty()) {
            return;
        }
        compiled.ifPresent(pipelines -> applyCompiled(effect, resources, pipelines));
        EffectStages stages = stagesOf(compiled);
        runPrewarm(effect, transform, resources, stages);
        simulateStep(effect, transform, resources, stages, delta);
        frame.submit(RenderPasses.TRANSPARENT_3D,
                DrawCommand.indirect(stages.draw(), quadMesh, resources.drawBindings(),
                        Long.MAX_VALUE, resources.indirectBuffer()));
    }

    private static void applyCompiled(ParticleEffect effect, VfxEffectResources resources,
                                      CompiledGraphPipelines pipelines) {
        effect.setEmissionRate(pipelines.sources().spawnRateAt(effect.normalizedTime()));
        resources.uploadLut(pipelines.lutPack());
    }

    private EffectStages stagesOf(Optional<CompiledGraphPipelines> compiled) {
        return new EffectStages(
                compiled.map(CompiledGraphPipelines::spawn).orElse(spawnPipeline),
                compiled.map(CompiledGraphPipelines::update).orElse(updatePipeline),
                compiled.map(CompiledGraphPipelines::draw).orElse(billboardPipeline));
    }

    private void runPrewarm(ParticleEffect effect, Transform3D transform,
                            VfxEffectResources resources, EffectStages stages) {
        int steps = effect.consumePrewarmSteps();
        for (int step = 0; step < steps; step++) {
            simulateStep(effect, transform, resources, stages, effect.prewarmStepSeconds());
        }
    }

    private void simulateStep(ParticleEffect effect, Transform3D transform,
                              VfxEffectResources resources, EffectStages stages, float delta) {
        Matrix4f world = transform.worldMatrix();
        world.getTranslation(emitterPosition);
        int spawnCount = effect.advanceEmission(delta, emitterPosition);
        writeEffectUbo(resources, emitterPosition, delta, spawnCount, effect);
        effect.recordSpawned(spawnCount);
        dispatchSimulation(resources, stages, spawnCount, effect.poolSize());
    }

    private void dispatchSimulation(VfxEffectResources resources, EffectStages stages,
                                    int spawnCount, int poolSize) {
        backend.dispatchCompute(ComputeDispatch.of(resetPipeline, resources.computeBindings(), 1));
        backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        if (spawnCount > 0) {
            backend.dispatchCompute(ComputeDispatch.of(stages.spawn(), resources.computeBindings(),
                    groupsFor(spawnCount)));
            backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        }
        backend.dispatchCompute(ComputeDispatch.of(stages.update(), resources.computeBindings(),
                groupsFor(poolSize)));
        backend.computeBarrier(ComputeBarrier.ALL);
    }

    private record EffectStages(PipelineHandle spawn, PipelineHandle update, PipelineHandle draw) {
    }

    private Optional<CompiledGraphPipelines> resolveGraphPipelines(ParticleEffect effect) {
        if (effect.graphPath().isEmpty()) {
            return Optional.empty();
        }
        Path graphFile = Path.of(effect.graphPath());
        long modifiedMillis = modifiedMillisOf(graphFile);
        CompiledGraphPipelines existing = compiledGraphs.get(effect);
        if (existing != null && existing.modifiedMillis() == modifiedMillis) {
            return existing.failed() ? Optional.empty() : Optional.of(existing);
        }
        if (existing != null) {
            destroyCompiled(existing);
        }
        CompiledGraphPipelines rebuilt = compileGraph(graphFile, modifiedMillis);
        compiledGraphs.put(effect, rebuilt);
        return rebuilt.failed() ? Optional.empty() : Optional.of(rebuilt);
    }

    private CompiledGraphPipelines compileGraph(Path graphFile, long modifiedMillis) {
        try {
            GraphAsset asset = new GraphJsonCodec().readFromFile(graphFile);
            VfxGraphCompiler.VfxCompiledSources sources = new VfxGraphCompiler(
                    shaderLoader.load("vfx/particle_common.glsl").source(),
                    shaderLoader.load("vfx/particle_shapes.glsl").source(),
                    shaderLoader.load("vfx/particle_noise.glsl").source())
                    .compile(asset, graphFile.toString());
            PipelineHandle spawn = backend.createComputePipeline(
                    new ComputePipelineDescriptor(sources.spawnCompute(), computeLayout));
            PipelineHandle update = backend.createComputePipeline(
                    new ComputePipelineDescriptor(sources.updateCompute(), computeLayout));
            PipelineHandle draw = createGraphBillboardPipeline(sources.fragmentBody());
            logger.info("[VfxRenderSystem] VFX graph compiled: " + graphFile.getFileName()
                    + " (rate " + sources.spawnRatePerSecond() + "/s)");
            return new CompiledGraphPipelines(modifiedMillis, false,
                    sources, spawn, update, draw, VfxLutPack.build(asset));
        } catch (RuntimeException | IOException error) {
            logger.error("[VfxRenderSystem] VFX graph failed to compile: " + graphFile, error);
            return new CompiledGraphPipelines(modifiedMillis, true, null, null, null, null, null);
        }
    }

    private PipelineHandle createGraphBillboardPipeline(String fragmentBody) {
        String fragment = """
                #version 430 core

                in vec2 particleCorner;
                in vec4 particleColor;

                out vec4 fragmentColor;

                void main() {
                %s
                }
                """.formatted(fragmentBody);
        ShaderSource source = new ShaderSource(
                shaderLoader.load("vfx/particle_billboard.vert.glsl").source(), fragment);
        return backend.createPipeline(new PipelineDescriptor(source, quadVertexLayout(),
                billboardState(), drawLayout));
    }

    private void destroyCompiled(CompiledGraphPipelines compiled) {
        if (compiled.failed()) {
            return;
        }
        backend.destroy(compiled.spawn());
        backend.destroy(compiled.update());
        backend.destroy(compiled.draw());
    }

    private static long modifiedMillisOf(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }

    private record CompiledGraphPipelines(long modifiedMillis, boolean failed,
                                          VfxGraphCompiler.VfxCompiledSources sources,
                                          PipelineHandle spawn, PipelineHandle update, PipelineHandle draw,
                                          VfxLutPack lutPack) {
    }

    private static int groupsFor(int threads) {
        return (threads + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
    }

    private void writeEffectUbo(VfxEffectResources resources, Vector3fc emitter, float delta,
                                int spawnCount, ParticleEffect effect) {
        Vector3fc motion = effect.frameMotion();
        effectUboScratch.clear();
        effectUboScratch.putFloat(emitter.x()).putFloat(emitter.y()).putFloat(emitter.z());
        effectUboScratch.putFloat(delta);
        effectUboScratch.putInt(spawnCount);
        effectUboScratch.putInt(effect.seed());
        effectUboScratch.putInt((int) effect.totalSpawned());
        effectUboScratch.putInt(effect.poolSize());
        effectUboScratch.putFloat(effect.normalizedTime());
        effectUboScratch.putFloat(effect.elapsedSeconds());
        effectUboScratch.putFloat(effect.durationSeconds());
        effectUboScratch.putFloat(effect.simulationSpaceFollow());
        effectUboScratch.putFloat(motion.x()).putFloat(motion.y()).putFloat(motion.z());
        effectUboScratch.putFloat(effect.distanceTravelled());
        effectUboScratch.flip();
        resources.writeEffectUbo(effectUboScratch);
    }

    private VfxEffectResources createResources(ParticleEffect effect) {
        VfxBindingLayouts layouts = new VfxBindingLayouts(computeLayout, drawLayout,
                meshRenderSystem.frameUniformBuffer());
        return new VfxEffectResources(backend, layouts, effect.poolSize());
    }

    private void purgeStale(List<ParticleEffect> seen) {
        Iterator<Map.Entry<ParticleEffect, VfxEffectResources>> iterator = effectResources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ParticleEffect, VfxEffectResources> entry = iterator.next();
            ParticleEffect effect = entry.getKey();
            if (seen.contains(effect)) {
                continue;
            }
            entry.getValue().destroy();
            iterator.remove();
            CompiledGraphPipelines compiled = compiledGraphs.remove(effect);
            if (compiled != null) {
                destroyCompiled(compiled);
            }
        }
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        for (VfxEffectResources resources : effectResources.values()) {
            resources.destroy();
        }
        effectResources.clear();
        for (CompiledGraphPipelines compiled : compiledGraphs.values()) {
            destroyCompiled(compiled);
        }
        compiledGraphs.clear();
        renderBackend.destroy(quadMesh);
        renderBackend.destroy(quadVertexBuffer);
        renderBackend.destroy(quadIndexBuffer);
        renderBackend.destroy(billboardPipeline);
        renderBackend.destroy(resetPipeline);
        renderBackend.destroy(spawnPipeline);
        renderBackend.destroy(updatePipeline);
    }
}
