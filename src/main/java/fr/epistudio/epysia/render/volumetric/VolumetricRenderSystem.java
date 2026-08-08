package fr.epistudio.epysia.render.volumetric;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.SceneTexture;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
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
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderSurface;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.StorageImageBinding;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.TextureWrap;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.environment.FullscreenQuad;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.logging.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VolumetricRenderSystem implements RenderSystem {
    public static final int VOLUMETRIC_ORDER = 350;
    public static final RenderPass VOLUMETRIC = RenderPasses.register("VOLUMETRIC", VOLUMETRIC_ORDER);

    private static final int MAXIMUM_VOLUMES = 8;
    private static final int VOXEL_LOCAL_SIZE = 128;
    private static final int RAYMARCH_LOCAL_SIZE = 8;
    private static final int NOISE_UBO_BYTES = 32;
    private static final RenderState COMPOSITE_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.ALPHA_BLEND, CullMode.NONE);

    private final ShaderLoader shaderLoader;
    private final RenderSurface surface;
    private final Logger logger;
    private final FullscreenQuad quad = new FullscreenQuad();
    private final ColliderOccupancyRasterizer rasterizer = new ColliderOccupancyRasterizer();
    private final Map<DensityVolume, DensityVolumeResources> resources = new HashMap<>();
    private final List<PendingVolume> pending = new ArrayList<>();
    private final float[] shapeScratch = new float[ColliderOccupancyRasterizer.MAXIMUM_SHAPES
            * ColliderOccupancyRasterizer.FLOATS_PER_SHAPE];
    private final ByteBuffer volumeUboScratch = BufferUtils.createByteBuffer(DensityVolumeResources.VOLUME_UBO_BYTES);
    private final ByteBuffer compositeUboScratch = BufferUtils.createByteBuffer(DensityVolumeResources.COMPOSITE_UBO_BYTES);
    private final ByteBuffer noiseUboScratch = BufferUtils.createByteBuffer(NOISE_UBO_BYTES);
    private final ByteBuffer shapeUploadScratch = BufferUtils.createByteBuffer(
            ColliderOccupancyRasterizer.MAXIMUM_SHAPES * ColliderOccupancyRasterizer.FLOATS_PER_SHAPE * Float.BYTES);
    private final ByteBuffer deformerScratch = BufferUtils.createByteBuffer(
            DensityDeformer.HARD_LIMIT * DensityDeformer.FLOATS_PER_ENTRY * Float.BYTES);
    private final Matrix4f scratchWorld = new Matrix4f();
    private final Matrix4f scratchInverse = new Matrix4f();
    private final Vector3f scratchVector = new Vector3f();
    private final Vector3f sunDirectionCache = new Vector3f(0.0f, -1.0f, 0.0f);
    private VolumetricNoiseSettings requestedNoiseSettings = VolumetricNoiseSettings.defaults();

    private RenderBackend backend;
    private StageConfigurer stageConfigurer;
    private VolumetricLayouts layouts;
    private BufferHandle shapeBuffer;
    private BufferHandle noiseUbo;
    private TextureHandle noiseVolume;
    private BindingSetHandle noiseBindings;
    private PipelineHandle noisePipeline;
    private PipelineHandle occupancyPipeline;
    private PipelineHandle clearPipeline;
    private PipelineHandle seedPipeline;
    private PipelineHandle propagatePipeline;
    private PipelineHandle swapPipeline;
    private PipelineHandle raymarchPipeline;
    private PipelineHandle compositePipeline;
    private VolumetricNoiseSettings uploadedNoise;
    private float elapsedSeconds;
    private int surfaceWidth = 1;
    private int surfaceHeight = 1;

    public VolumetricRenderSystem(ShaderLoader shaderLoader, RenderSurface surface, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.surface = surface;
        this.logger = logger;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.stageConfigurer = configurer;
        quad.initialize(backend);
        createSharedResources();
        createPipelines();
        configurer.bindStageTargetFollowing(VOLUMETRIC, RenderPasses.TRANSPARENT_3D, PassClear.none());
        configurer.bindStagePreparation(VOLUMETRIC, this::runComputePasses);
        adoptSurfaceSize();
    }

    private void adoptSurfaceSize() {
        surfaceWidth = Math.max(1, surface.framebufferWidth());
        surfaceHeight = Math.max(1, surface.framebufferHeight());
    }

    private void createSharedResources() {
        long shapeBytes = (long) ColliderOccupancyRasterizer.MAXIMUM_SHAPES
                * ColliderOccupancyRasterizer.FLOATS_PER_SHAPE * Float.BYTES;
        shapeBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer((int) shapeBytes)));
        layouts = new VolumetricLayouts(shapeBuffer, shapeBytes);
        noiseUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(NOISE_UBO_BYTES), true));
        noiseVolume = backend.createTexture(TextureDescriptor.volume(
                VolumetricNoiseSettings.RESOLUTION, VolumetricNoiseSettings.RESOLUTION,
                VolumetricNoiseSettings.RESOLUTION, TextureFormat.R16F, TextureUsage.SAMPLED,
                SamplerFilter.LINEAR, TextureWrap.REPEAT));
        noiseBindings = backend.createBindingSet(new BindingSetDescriptor(VolumetricLayouts.noiseLayout(), List.of(
                new Binding(0, UniformBufferBinding.whole(noiseUbo, NOISE_UBO_BYTES)),
                new Binding(1, StorageImageBinding.writeOnly(noiseVolume)))));
    }

    private void createPipelines() {
        noisePipeline = computePipeline("volumetric/worley_noise.comp.glsl", VolumetricLayouts.noiseLayout());
        occupancyPipeline = computePipeline("volumetric/occupancy.comp.glsl", VolumetricLayouts.occupancyLayout());
        clearPipeline = computePipeline("volumetric/density_clear.comp.glsl", VolumetricLayouts.densityLayout());
        seedPipeline = computePipeline("volumetric/density_seed.comp.glsl", VolumetricLayouts.densityLayout());
        propagatePipeline = computePipeline("volumetric/density_propagate.comp.glsl", VolumetricLayouts.densityLayout());
        swapPipeline = computePipeline("volumetric/density_swap.comp.glsl", VolumetricLayouts.densityLayout());
        raymarchPipeline = computePipeline("volumetric/volumetric_raymarch.comp.glsl", VolumetricLayouts.raymarchLayout());
        compositePipeline = backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(shaderLoader.load("post.vert.glsl").source(),
                        shaderLoader.load("volumetric/volumetric_composite.frag.glsl").source()),
                FullscreenQuad.LAYOUT, COMPOSITE_STATE, VolumetricLayouts.compositeLayout()));
    }

    private PipelineHandle computePipeline(String path, BindingSetLayout layout) {
        return backend.createComputePipeline(new ComputePipelineDescriptor(
                shaderLoader.load(path).source(), layout));
    }

    @Override
    public void onResize(RenderBackend renderBackend, StageConfigurer configurer, int width, int height) {
        surfaceWidth = Math.max(1, width);
        surfaceHeight = Math.max(1, height);
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        pending.clear();
        List<DensityVolume> volumes = scene.componentsOf(DensityVolume.class);
        if (volumes.isEmpty() || context.primaryCamera().isEmpty()) {
            purgeStale(volumes);
            return;
        }
        adoptSurfaceSize();
        elapsedSeconds += 1.0f / 60.0f;
        refreshSunDirection(scene);
        int shapeCount = uploadShapes(scene, volumes);
        Camera3D camera = context.primaryCamera().get();
        for (DensityVolume volume : volumes) {
            prepareVolume(scene, volume, camera, context, shapeCount, frame);
        }
        purgeStale(volumes);
    }

    private int uploadShapes(Scene scene, List<DensityVolume> volumes) {
        int layerMask = 0;
        for (DensityVolume volume : volumes) {
            if (volume.occupancyFromColliders()) {
                layerMask |= volume.occupancyLayers();
            }
        }
        if (layerMask == 0) {
            return 0;
        }
        int count = rasterizer.pack(scene, layerMask, shapeScratch);
        shapeUploadScratch.clear();
        shapeUploadScratch.asFloatBuffer().put(shapeScratch, 0,
                count * ColliderOccupancyRasterizer.FLOATS_PER_SHAPE);
        shapeUploadScratch.limit(Math.max(1, count * ColliderOccupancyRasterizer.FLOATS_PER_SHAPE * Float.BYTES));
        backend.writeBuffer(shapeBuffer, shapeUploadScratch, 0L);
        return count;
    }

    private void prepareVolume(Scene scene, DensityVolume volume, Camera3D camera,
                               RenderContext context, int shapeCount, FrameBuilder frame) {
        if (pending.size() >= MAXIMUM_VOLUMES || !volume.withinVoxelBudget()) {
            warnBudget(volume);
            return;
        }
        VolumetricRenderer renderer = componentOf(volume, VolumetricRenderer.class);
        if (renderer == null) {
            return;
        }
        requestedNoiseSettings = renderer.noiseSettings();
        DensityVolumeResources volumeResources = resourcesFor(volume, renderer);
        DensityPropagation propagation = componentOf(volume, DensityPropagation.class);
        DensityDeformer deformer = componentOf(volume, DensityDeformer.class);
        advanceSources(propagation, deformer);
        writeUniforms(volume, renderer, propagation, deformer, camera, context, shapeCount, volumeResources);
        pending.add(new PendingVolume(volume, volumeResources, propagation, shapeCount));
        frame.submit(VOLUMETRIC, DrawCommand.of(compositePipeline, quad.mesh(),
                volumeResources.compositeBindings(), pending.size()));
    }

    private void advanceSources(DensityPropagation propagation, DensityDeformer deformer) {
        float delta = 1.0f / 60.0f;
        if (propagation != null) {
            seedAutomatically(propagation);
            propagation.advance(delta);
        }
        if (deformer != null) {
            deformer.advance(delta);
        }
    }

    private static void seedAutomatically(DensityPropagation propagation) {
        if (!propagation.awaitingAutomaticSeed()) {
            return;
        }
        propagation.owner()
                .flatMap(gameObject -> gameObject.getComponent(Transform3D.class))
                .ifPresent(transform -> propagation.seed(transform.position()));
    }

    private static <T extends IComponent> T componentOf(DensityVolume volume, Class<T> componentClass) {
        return volume.owner().map(gameObject -> gameObject.getComponentOrNull(componentClass)).orElse(null);
    }

    private DensityVolumeResources resourcesFor(DensityVolume volume, VolumetricRenderer renderer) {
        DensityVolumeResources existing = resources.get(volume);
        if (existing == null || existing.voxelCount() != volume.voxelCount()) {
            if (existing != null) {
                existing.destroy();
            }
            existing = new DensityVolumeResources(backend, layouts, volume.voxelCount());
            resources.put(volume, existing);
            volume.markOccupancyDirty();
        }
        int width = renderer.resolution().scale(surfaceWidth);
        int height = renderer.resolution().scale(surfaceHeight);
        if (!existing.matchesTargetSize(width, height)) {
            existing.resizeTargets(width, height, noiseVolume, sceneDepthTexture());
        }
        return existing;
    }

    private TextureHandle sceneDepthTexture() {
        return stageConfigurer.sceneTexture(SceneTexture.SCENE_DEPTH).orElse(noiseVolume);
    }

    private void writeUniforms(DensityVolume volume, VolumetricRenderer renderer,
                               DensityPropagation propagation, DensityDeformer deformer,
                               Camera3D camera, RenderContext context, int shapeCount,
                               DensityVolumeResources volumeResources) {
        float alpha = context.interpolationAlpha();
        volume.worldMatrix(scratchWorld, alpha);
        scratchWorld.invert(scratchInverse);
        volumeUboScratch.clear();
        scratchInverse.get(0, volumeUboScratch);
        scratchWorld.get(64, volumeUboScratch);
        camera.projection().invert(new Matrix4f()).get(128, volumeUboScratch);
        camera.view(alpha).invert(new Matrix4f()).get(192, volumeUboScratch);
        camera.viewProjection(alpha).invert(new Matrix4f()).get(256, volumeUboScratch);
        writeVectors(volume, renderer, propagation, camera, alpha);
        writeParameters(volume, renderer, deformer, shapeCount, volumeResources);
        volumeUboScratch.position(DensityVolumeResources.VOLUME_UBO_BYTES).flip();
        volumeResources.writeVolumeUniforms(volumeUboScratch);
        writeCompositeUniforms(renderer, volumeResources);
        writeDeformerBuffer(deformer, volumeResources);
    }

    private void writeVectors(DensityVolume volume, VolumetricRenderer renderer,
                              DensityPropagation propagation, Camera3D camera, float alpha) {
        putVector(320, volume.extents().x(), volume.extents().y(), volume.extents().z(), 0.0f);
        putVector(336, volume.resolutionX(), volume.resolutionY(), volume.resolutionZ(), 0.0f);
        Vector3f radius = propagation == null ? scratchVector.set(0.0f) : scratchVector.set(propagation.growthRadius());
        putVector(352, radius.x, radius.y, radius.z, 0.0f);
        Vector3f seed = propagation == null ? scratchVector.set(0.0f) : scratchVector.set(propagation.seedPoint());
        putVector(368, seed.x, seed.y, seed.z, 0.0f);
        camera.position(scratchVector, alpha);
        putVector(384, scratchVector.x, scratchVector.y, scratchVector.z, 0.0f);
        putVector(400, sunDirection().x, sunDirection().y, sunDirection().z, 0.0f);
        putVector(416, renderer.albedo().x(), renderer.albedo().y(), renderer.albedo().z(), 0.0f);
        putVector(432, renderer.lightColor().x(), renderer.lightColor().y(), renderer.lightColor().z(), 0.0f);
        putVector(448, renderer.extinctionColor().x(), renderer.extinctionColor().y(),
                renderer.extinctionColor().z(), 0.0f);
        putVector(464, renderer.animationDirection().x(), renderer.animationDirection().y(),
                renderer.animationDirection().z(), 0.0f);
    }

    private void writeParameters(DensityVolume volume, VolumetricRenderer renderer, DensityDeformer deformer,
                                 int shapeCount, DensityVolumeResources volumeResources) {
        putVector(480, volume.voxelSize(), renderer.stepSize(), renderer.lightStepSize(), renderer.detailScale());
        putVector(496, renderer.absorption(), renderer.scattering(),
                renderer.volumeDensity() * renderer.stepSize(),
                renderer.shadowDensity() * renderer.lightStepSize());
        DensityPropagation propagation = componentOf(volume, DensityPropagation.class);
        float propagationDistance = propagation == null ? 16.0f : propagation.propagationDistance();
        putVector(512, renderer.densityFalloff(), renderer.alphaThreshold(), renderer.anisotropy(), propagationDistance);
        putVector(528, volumeResources.targetWidth(), volumeResources.targetHeight(),
                renderer.stepCount(), renderer.lightStepCount());
        putVector(544, elapsedSeconds, renderer.phaseFunction().ordinal(), volume.voxelCount(), shapeCount);
        int deformerCount = deformer == null ? 0 : deformer.activeCount();
        float deformerDepth = deformer == null ? 0.0f : deformer.depth();
        putVector(560, deformerCount, deformerDepth, 0.0f, 0.0f);
    }

    private void putVector(int offset, float x, float y, float z, float w) {
        volumeUboScratch.putFloat(offset, x).putFloat(offset + 4, y)
                .putFloat(offset + 8, z).putFloat(offset + 12, w);
    }

    private Vector3f sunDirection() {
        return sunDirectionCache;
    }

    private void refreshSunDirection(Scene scene) {
        List<DirectionalLight> lights = scene.componentsOf(DirectionalLight.class);
        if (lights.isEmpty()) {
            sunDirectionCache.set(0.0f, -1.0f, 0.0f);
            return;
        }
        lights.getFirst().direction(sunDirectionCache);
    }

    private void writeCompositeUniforms(VolumetricRenderer renderer, DensityVolumeResources volumeResources) {
        compositeUboScratch.clear();
        compositeUboScratch.putFloat(renderer.sharpness())
                .putFloat(renderer.bicubicUpscale() ? 1.0f : 0.0f)
                .putFloat(renderer.debugView().ordinal())
                .putFloat(0.0f);
        compositeUboScratch.flip();
        volumeResources.writeCompositeUniforms(compositeUboScratch);
    }

    private void writeDeformerBuffer(DensityDeformer deformer, DensityVolumeResources volumeResources) {
        if (deformer == null || deformer.activeCount() == 0) {
            return;
        }
        int floats = deformer.activeCount() * DensityDeformer.FLOATS_PER_ENTRY;
        deformerScratch.clear();
        deformerScratch.asFloatBuffer().put(deformer.packedEntries(), 0, floats);
        deformerScratch.limit(floats * Float.BYTES);
        volumeResources.writeDeformers(deformerScratch);
    }

    private void runComputePasses() {
        ensureNoiseVolume();
        for (PendingVolume entry : pending) {
            runVolumeCompute(entry);
        }
        backend.computeBarrier(ComputeBarrier.STORAGE_IMAGE);
        backend.computeBarrier(ComputeBarrier.TEXTURE_FETCH);
    }

    private void runVolumeCompute(PendingVolume entry) {
        DensityVolumeResources volumeResources = entry.resources();
        int voxelGroups = groupCount(volumeResources.voxelCount(), VOXEL_LOCAL_SIZE);
        if (entry.volume().occupancyDirty()) {
            backend.dispatchCompute(ComputeDispatch.of(occupancyPipeline,
                    volumeResources.occupancyBindings(), voxelGroups));
            backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
            entry.volume().clearOccupancyDirty();
        }
        runPropagation(entry, voxelGroups);
        dispatchRaymarch(volumeResources);
    }

    private void runPropagation(PendingVolume entry, int voxelGroups) {
        DensityPropagation propagation = entry.propagation();
        if (propagation == null) {
            return;
        }
        DensityVolumeResources volumeResources = entry.resources();
        if (propagation.consumeSeedRequest()) {
            backend.dispatchCompute(ComputeDispatch.of(clearPipeline, volumeResources.densityBindings(), voxelGroups));
            backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
            backend.dispatchCompute(ComputeDispatch.of(seedPipeline, volumeResources.densityBindings(), 1));
            backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        }
        if (!propagation.growing()) {
            return;
        }
        backend.dispatchCompute(ComputeDispatch.of(propagatePipeline, volumeResources.densityBindings(), voxelGroups));
        backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        backend.dispatchCompute(ComputeDispatch.of(swapPipeline, volumeResources.densityBindings(), voxelGroups));
        backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
    }

    private void dispatchRaymarch(DensityVolumeResources volumeResources) {
        backend.dispatchCompute(new ComputeDispatch(raymarchPipeline, volumeResources.raymarchBindings(),
                groupCount(volumeResources.targetWidth(), RAYMARCH_LOCAL_SIZE),
                groupCount(volumeResources.targetHeight(), RAYMARCH_LOCAL_SIZE), 1));
    }

    private static int groupCount(int total, int localSize) {
        return Math.max(1, (total + localSize - 1) / localSize);
    }

    private void ensureNoiseVolume() {
        VolumetricNoiseSettings settings = requestedNoiseSettings;
        if (settings.equals(uploadedNoise)) {
            return;
        }
        uploadedNoise = settings;
        noiseUboScratch.clear();
        noiseUboScratch.putFloat(settings.seed()).putFloat(settings.octaves())
                .putFloat(settings.cellSize()).putFloat(settings.axisCellCount());
        noiseUboScratch.putFloat(settings.amplitude()).putFloat(settings.warp())
                .putFloat(settings.bias()).putFloat(settings.inverted() ? 1.0f : 0.0f);
        noiseUboScratch.flip();
        backend.writeBuffer(noiseUbo, noiseUboScratch, 0L);
        int groups = settings.groupCount();
        backend.dispatchCompute(new ComputeDispatch(noisePipeline, noiseBindings, groups, groups, groups));
        backend.computeBarrier(ComputeBarrier.TEXTURE_FETCH);
    }

    private void warnBudget(DensityVolume volume) {
        if (!volume.withinVoxelBudget()) {
            logger.warn("Density volume exceeds the voxel budget of " + DensityVolume.MAXIMUM_VOXELS
                    + " voxels and was skipped. Increase Voxel Size or reduce Extents.");
        }
    }

    private void purgeStale(List<DensityVolume> alive) {
        resources.keySet().removeIf(volume -> {
            if (alive.contains(volume)) {
                return false;
            }
            resources.get(volume).destroy();
            return true;
        });
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        resources.values().forEach(DensityVolumeResources::destroy);
        resources.clear();
        renderBackend.destroy(noiseBindings);
        renderBackend.destroy(noiseVolume);
        renderBackend.destroy(noiseUbo);
        renderBackend.destroy(shapeBuffer);
        renderBackend.destroy(noisePipeline);
        renderBackend.destroy(occupancyPipeline);
        renderBackend.destroy(clearPipeline);
        renderBackend.destroy(seedPipeline);
        renderBackend.destroy(propagatePipeline);
        renderBackend.destroy(swapPipeline);
        renderBackend.destroy(raymarchPipeline);
        renderBackend.destroy(compositePipeline);
        quad.shutdown();
    }

    private record PendingVolume(DensityVolume volume, DensityVolumeResources resources,
                                 DensityPropagation propagation, int shapeCount) {
    }
}
