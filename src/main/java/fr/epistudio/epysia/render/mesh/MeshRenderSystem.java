package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.animation.AnimationLayer;
import fr.epistudio.epysia.animation.BindPose;
import fr.epistudio.epysia.animation.BindPoseCache;
import fr.epistudio.epysia.animation.BlendSpaceSampler;
import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipSampler;
import fr.epistudio.epysia.concurrent.FrameWorkers;
import fr.epistudio.epysia.animation.PoseLayerBlend;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.animation.SkeletonPose;
import fr.epistudio.epysia.animation.SkinningPalette;
import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.Component;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.LightProbeVolume;
import fr.epistudio.epysia.components.MeshRenderSource;
import fr.epistudio.epysia.components.JointSocket;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.components.RenderLayers;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.Skybox;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.profiling.FrameProfiler;
import fr.epistudio.epysia.project.RenderTuning;
import fr.epistudio.epysia.render.ProfiledRenderSystem;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.SceneTexture;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.ComputeBarrier;
import fr.epistudio.epysia.render.backend.ComputeDispatch;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.environment.Environment;
import fr.epistudio.epysia.render.environment.SkySource;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialClassMetadata;
import fr.epistudio.epysia.render.material.MaterialClassMetadata.TextureFieldDescriptor;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MeshRenderSystem implements RenderSystem, ProfiledRenderSystem {
    private FrameProfiler profiler = new FrameProfiler();

    private static final int FRAME_VIEW_PROJECTION_BYTES = 64;
    private static final String SHADOW_MASK_ALBEDO_FIELD = "albedo";
    private static final long SINGLE_CASTER_DOMAIN = 0x51A71C0000000001L;
    private static final long INSTANCED_CASTER_DOMAIN = 0x1B5AC70000000002L;

    private static final Vector3f[] CUBE_DIRECTIONS = {
            new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(-1.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f(0.0f, -1.0f, 0.0f),
            new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f(0.0f, 0.0f, -1.0f)
    };
    private static final Vector3f[] CUBE_UPS = {
            new Vector3f(0.0f, -1.0f, 0.0f), new Vector3f(0.0f, -1.0f, 0.0f),
            new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f(0.0f, 0.0f, -1.0f),
            new Vector3f(0.0f, -1.0f, 0.0f), new Vector3f(0.0f, -1.0f, 0.0f)
    };

    private static final String DEPTH_PREPASS_VERTEX_PATH = "depth_prepass.vert.glsl";
    private static final String DEPTH_PREPASS_FRAGMENT_PATH = "depth_prepass.frag.glsl";
    private static final String MASKED_DEPTH_PREPASS_VERTEX_PATH = "depth_prepass_masked.vert.glsl";
    private static final String MASKED_DEPTH_PREPASS_FRAGMENT_PATH = "depth_prepass_masked.frag.glsl";

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final SurfaceShadowVariants depthPrepassVariants;
    private final SurfaceShadowVariants maskedDepthPrepassVariants;
    private boolean depthPrepassEnabled = Boolean.getBoolean("epysia.depthPrepass");
    private int gpuCullMinimumInstances = RenderTuning.DEFAULT_GPU_CULL_MINIMUM_INSTANCES;
    private final ShadowStatistics shadowStatistics = new ShadowStatistics();
    private final Logger logger;
    private final SurfaceTimeDependence surfaceTimeDependence;
    private boolean shadowCachingEnabled = true;
    private boolean shadowSplitEnabled = true;
    private final FrameUboWriter frameUboWriter = new FrameUboWriter();
    private final LightStorage lightStorage = new LightStorage();
    private final ProbeGridStorage probeGrid = new ProbeGridStorage();
    private Optional<BakedProbes> activeProbes = Optional.empty();
    private final ClusterLightCuller clusterCuller = new ClusterLightCuller();
    private boolean clusteringEnabled = true;
    private final CascadedShadowMaps shadowCascades;
    private final SpotShadowAtlas spotShadows;
    private final PointShadowAtlas pointShadows;
    private final MaterialPipelineCache materialCache;
    private final SurfaceUniformBinder surfaceUniforms;
    private final MeshInstanceBatches instanceBatches = new MeshInstanceBatches();
    private final MaterialStateDigests materialStates = new MaterialStateDigests();
    private boolean instancingEnabled = true;
    private int batchesThisFrame;
    private int instancedBatchesThisFrame;
    private final FrustumCuller culler = new FrustumCuller();
    private final SceneRenderIndex sceneRenderIndex = new SceneRenderIndex();
    private final SceneRelevance sceneRelevance = new SceneRelevance();
    private final List<MeshRenderer> unindexedRenderers = new ArrayList<>();
    private PreparedEntry[] preparedBySlot = new PreparedEntry[0];
    private final Map<MeshInstanceBatch, GpuCullResources> cullResources = new IdentityHashMap<>();
    private final Map<MeshInstanceBatch, BindingSetHandle> indirectBindings = new IdentityHashMap<>();
    private final ByteBuffer scratchCullParameters =
            BufferUtils.createByteBuffer(GpuInstanceCuller.PARAMETERS_BYTES);
    private final org.joml.Vector4f scratchFrustumPlane = new org.joml.Vector4f();
    private Material memoPipelineMaterial;
    private MaterialClassResources memoPipelineClassResources;
    private boolean memoPipelineSkinned;
    private boolean memoPipelineColored;
    private boolean memoPipelineLightmapped;
    private boolean pipelineMemoEnabled =
            Boolean.parseBoolean(System.getProperty("epysia.render.pipelineMemo", "true"));
    private boolean transformLookupCached =
            Boolean.parseBoolean(System.getProperty("epysia.render.cachedTransformLookup", "true"));
    private long animationGeneration = -1L;
    private int animationPhaseCounter;
    private long posesSampled;
    private final Map<GameObject, JointPalette> animatedPalettes = new IdentityHashMap<>();
    private final BindPoseCache bindPoseCache = new BindPoseCache();
    private SkinnedDeformer skinnedDeformer;
    private boolean skinOnceEnabled =
            Boolean.parseBoolean(System.getProperty("epysia.render.skinOnce", "true"));
    private final PoseUpdateSchedule poseSchedule = new PoseUpdateSchedule();
    private final Vector3f scratchAnimationMinimum = new Vector3f();
    private final Vector3f scratchAnimationMaximum = new Vector3f();
    private DepthPyramid depthPyramid;
    private GpuInstanceCuller instanceCuller;
    private boolean gpuCullingEnabled =
            Boolean.parseBoolean(System.getProperty("epysia.render.gpuCulling", "false"));
    private TextureHandle pyramidSourceDepth;
    private final CpuOcclusionGrid occlusionGrid = new CpuOcclusionGrid();
    private static final float INDEX_MINIMUM_PRUNED_FRACTION =
            Float.parseFloat(System.getProperty("epysia.render.sceneIndexMinimumPruned", "0.30"));
    private static final int INDEX_IDLE_FRAMES_BEFORE_BYPASS = 3;
    private static final int INDEX_BYPASS_FRAMES = 120;

    private boolean sceneIndexEnabled =
            Boolean.parseBoolean(System.getProperty("epysia.render.sceneIndex", "true"));
    private float indexAlpha;
    private int skippedByIndexThisFrame;
    private int skippedByRelevanceThisFrame;
    private int occludedThisFrame;
    private static final boolean LOOP_PROFILING = Boolean.getBoolean("epysia.render.loopProfiling");
    private long phaseBoundsNanos;
    private long phaseResolveNanos;
    private long phaseMaterialNanos;
    private long phaseSubmitNanos;
    private int loopProfileFrames;
    private boolean indexBypassed;
    private int indexIdleFrames;
    private int indexBypassFrames;

    private final Map<Material, Long> materialSignatures = new IdentityHashMap<>();
    private final Map<Material, Boolean> materialChangedCache = new IdentityHashMap<>();
    private final Map<MeshRenderSource, RenderableMesh> objectResources = new IdentityHashMap<>();
    private final Map<MeshRenderSource, CachedWorldBounds> boundsCache = new IdentityHashMap<>();
    private final Set<UploadedMesh> mismatchedLevelMeshes = Collections.newSetFromMap(new IdentityHashMap<>());
    private int boundsCacheHits;
    private int boundsCacheMisses;
    private ObjectUniformArena objectUniforms;
    private boolean ringObjectUniforms =
            Boolean.parseBoolean(System.getProperty("epysia.render.ringObjectUniforms", "false"));
    private final Vector3f scratchCasterMin = new Vector3f();
    private final Vector3f scratchCasterMax = new Vector3f();
    private final Vector3f scratchTileMin = new Vector3f();
    private final Vector3f scratchTileMax = new Vector3f();
    private int renderFrameCounter;
    private int renderersSeenCount;
    private int renderableGeneration;
    private long lastSceneVersion = Long.MIN_VALUE;
    private long sceneVersionAtPurge = Long.MIN_VALUE;
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();
    private final Set<BindingSetHandle> ownedBindings = new LinkedHashSet<>();
    private final List<Light> activeLights = new ArrayList<>(64);
    private final List<Light> scratchDirectionalLights = new ArrayList<>(8);
    private final List<Light> scratchOtherLights = new ArrayList<>(64);
    private final ByteBuffer scratchObjectUbo = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
    private final ByteBuffer scratchFrameViewProjection = BufferUtils.createByteBuffer(FRAME_VIEW_PROJECTION_BYTES);
    private final Matrix4f lastCameraViewProjection = new Matrix4f();
    private final Matrix4f scratchNormalMatrix = new Matrix4f();
    private final Vector3f scratchSunDirection = new Vector3f();
    private final Vector3f scratchLightDirection = new Vector3f();
    private final Vector3f scratchCameraPosition = new Vector3f();
    private final Matrix4f scratchSpotMatrix = new Matrix4f();
    private final Vector3f scratchSpotPosition = new Vector3f();
    private final Vector3f scratchSpotDirection = new Vector3f();
    private final Vector3f scratchSpotTarget = new Vector3f();
    private final Vector3f scratchSpotUp = new Vector3f();
    private final Matrix4f[] scratchPointFaces = createPointFaceMatrices();
    private final Vector3f scratchPointPosition = new Vector3f();
    private final Vector3f scratchPointTarget = new Vector3f();
    private final Environment environment;
    private float shadowDistance = 60.0f;
    private float appliedSkyIntensity = Float.NaN;
    private float appliedAmbientIntensity = Float.NaN;
    private LitMaterial fallback;

    private StageConfigurer stageConfigurer;
    private TextureHandle lastOpaqueColorTexture;
    private boolean frontToBackOpaque =
            Boolean.parseBoolean(System.getProperty("epysia.render.frontToBack", "true"));
    private boolean multiDrawEnabled =
            Boolean.parseBoolean(System.getProperty("epysia.render.multiDraw", "true"));

    private final Map<MultiDrawKey, MultiDrawGroup> multiDrawGroups = new LinkedHashMap<>();
    private final Set<MultiDrawGroup> multiDrawPending =
            Collections.newSetFromMap(new LinkedHashMap<>());
    private int multiDrawsThisFrame;
    private int multiDrawGeometriesThisFrame;
    private FrameWorkers frameWorkers;
    private PoseSamplingContext[] poseContexts = PoseSamplingContext.create(1);
    private PendingPose[] pendingPoses = new PendingPose[0];
    private int pendingPoseCount;
    private static final int MINIMUM_POSES_PER_WORKER =
            Integer.getInteger("epysia.animation.posesPerWorker", 24);
    private boolean parallelAnimation =
            Boolean.parseBoolean(System.getProperty("epysia.animation.parallel", "true"));
    private RenderBackend backend;
    private int activeCullMask = RenderLayers.ALL;
    private int culledThisFrame;
    private int submittedThisFrame;
    private long startNanos;
    private long lastFrameNanos;
    private float frameDeltaSeconds;

    public MeshRenderSystem(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger) {
        this.logger = logger;
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.depthPrepassVariants = new SurfaceShadowVariants(shaderLoader, shaderWatcher, logger,
                DEPTH_PREPASS_VERTEX_PATH, DEPTH_PREPASS_FRAGMENT_PATH,
                RenderState.OPAQUE_3D.withoutColorWrite(), this::invalidateShadowCaches);
        this.maskedDepthPrepassVariants = new SurfaceShadowVariants(shaderLoader, shaderWatcher, logger,
                MASKED_DEPTH_PREPASS_VERTEX_PATH, MASKED_DEPTH_PREPASS_FRAGMENT_PATH,
                RenderState.OPAQUE_3D.withoutColorWrite(), this::invalidateShadowCaches, true);
        this.surfaceTimeDependence = new SurfaceTimeDependence(shaderLoader, logger);
        this.shadowCascades = new CascadedShadowMaps(shaderLoader, shaderWatcher, logger,
                shadowStatistics, this::invalidateShadowCaches);
        this.spotShadows = new SpotShadowAtlas(shaderLoader, shaderWatcher, logger,
                shadowStatistics, this::invalidateShadowCaches);
        this.pointShadows = new PointShadowAtlas(shaderLoader, shaderWatcher, logger,
                shadowStatistics, this::invalidateShadowCaches);
        this.materialCache = new MaterialPipelineCache(shaderLoader, shaderWatcher, logger);
        this.surfaceUniforms = new SurfaceUniformBinder(logger);
        this.environment = new Environment(shaderLoader);
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.stageConfigurer = configurer;
        this.startNanos = System.nanoTime();
        this.lastFrameNanos = this.startNanos;
        shadowCascades.initialize(backend);
        configurer.bindStagePreparation(RenderPasses.OPAQUE_3D, shadowCascades::render);
        spotShadows.initialize(backend, shadowCascades.cascadeUbo());
        configurer.bindStagePreparation(RenderPasses.OPAQUE_3D, spotShadows::render);
        pointShadows.initialize(backend, shadowCascades.cascadeUbo());
        configurer.bindStagePreparation(RenderPasses.OPAQUE_3D, pointShadows::render);
        depthPyramid = new DepthPyramid(backend, shaderLoader);
        instanceCuller = new GpuInstanceCuller(backend, shaderLoader);
        configurer.bindStagePreparation(RenderPasses.OPAQUE_3D, this::refreshDepthPyramid);
        materialCache.initialize(backend);
        surfaceUniforms.initialize(backend);
        frameUboWriter.initialize(backend);
        lightStorage.initialize(backend);
        probeGrid.initialize(backend);
        clusterCuller.initialize(backend);
        instanceBatches.initialize(backend, this::createInstanceBindingSet);
        depthPrepassVariants.initialize(backend, shadowCascades.bindingLayout(), createDepthPrepassPipeline());
        maskedDepthPrepassVariants.initialize(backend, shadowCascades.maskedBindingLayout(),
                createMaskedDepthPrepassPipeline());
        environment.initialize(backend);
    }

    public void setProfiler(FrameProfiler frameProfiler) {
        this.profiler = frameProfiler;
    }

    private void advanceFrameClock(long nowNanos) {
        float delta = (nowNanos - lastFrameNanos) / 1_000_000_000.0f;
        frameDeltaSeconds = Math.max(0.0f, Math.min(0.25f, delta));
        lastFrameNanos = nowNanos;
    }

    private long markSection(String name, long since) {
        long now = System.nanoTime();
        profiler.record(name, now - since);
        return now;
    }

    public Optional<BufferHandle> instanceBufferFor(MultiMeshRenderer renderer) {
        return instanceBatches.instanceBufferFor(renderer);
    }

    public void writeProbeCoefficients(int probeIndex, float[] coefficients) {
        probeGrid.writeProbe(probeIndex, coefficients);
    }

    public long posesSampled() {
        return posesSampled;
    }

    public int animationsCulledThisFrame() {
        return poseSchedule.culledThisFrame();
    }

    public int animationsCadencedThisFrame() {
        return poseSchedule.cadencedThisFrame();
    }

    public int skinnedDeformationsThisFrame() {
        return skinnedDeformer == null ? 0 : skinnedDeformer.deformedThisFrame();
    }

    public void setAnimationCullingEnabled(boolean value) {
        poseSchedule.setCullingEnabled(value);
    }

    public boolean animationCullingEnabled() {
        return poseSchedule.cullingEnabled();
    }

    public void setAnimationFullRateDistance(float value) {
        poseSchedule.setFullRateDistance(value);
    }

    public void setGpuCullMinimumInstances(int value) {
        this.gpuCullMinimumInstances = Math.clamp(value,
                RenderTuning.MINIMUM_GPU_CULL_INSTANCES, RenderTuning.MAXIMUM_GPU_CULL_INSTANCES);
    }

    public int gpuCullMinimumInstances() {
        return gpuCullMinimumInstances;
    }

    public float animationFullRateDistance() {
        return poseSchedule.fullRateDistance();
    }

    Optional<BufferHandle> deformedVertexBuffer(MeshRenderer renderer) {
        RenderableMesh renderable = objectResources.get(renderer);
        if (renderable == null) {
            return Optional.empty();
        }
        return renderable.deformedMesh().map(DeformedMesh::vertexBuffer);
    }

    public Optional<StorageBufferBinding> jointPaletteBinding(MeshRenderer renderer) {
        RenderableMesh renderable = objectResources.get(renderer);
        if (renderable == null) {
            return Optional.empty();
        }
        return renderable.jointPalette()
                .map(palette -> StorageBufferBinding.whole(palette.buffer(), palette.byteSize()));
    }

    public BufferHandle frameUniformBuffer() {
        return frameUboWriter.handle();
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        long mark = System.nanoTime();
        advanceFrameClock(mark);
        shaderWatcher.poll();
        Camera3D camera = context.primaryCamera().orElse(null);
        if (camera == null) {
            writeFallbackFrameViewProjection();
            return;
        }
        Optional<DirectionalLight> primaryDirectional = gatherLights(scene);
        resolveSunDirection(primaryDirectional);
        refreshSceneTextureBindings();
        applySkybox(scene);
        environment.prepareFrame(scratchSunDirection);
        refreshProbeLighting(scene);
        mark = markSection("mesh/lights", mark);
        shadowStatistics.beginFrame();
        long sceneModificationCount = scene.modificationCount();
        lastSceneVersion = sceneModificationCount;
        shadowCascades.beginFrame(sceneModificationCount);
        spotShadows.beginFrame(sceneModificationCount);
        pointShadows.beginFrame(sceneModificationCount);
        float alpha = context.interpolationAlpha();
        if (primaryDirectional.isPresent()) {
            primaryDirectional.get().direction(scratchLightDirection).normalize();
            shadowCascades.update(camera, scratchLightDirection, shadowDistance, alpha);
        }
        assignSpotShadows();
        assignPointShadows();
        mark = markSection("mesh/shadowSetup", mark);
        float timeSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
        clusterCuller.cull(camera, activeLights, alpha);
        mark = markSection("mesh/clusterCull", mark);
        frameUboWriter.write(camera, primaryDirectional, activeLights, timeSeconds,
                environment.settings().ambientIntensity(), shadowCascades, spotShadows, pointShadows,
                clusterCuller, clusteringEnabled, alpha, activeProbes);
        lastCameraViewProjection.set(camera.cullingViewProjection(alpha));
        lightStorage.update(activeLights, spotShadows, pointShadows);
        mark = markSection("mesh/uniforms", mark);
        materialCache.beginFrame();
        materialStates.beginFrame();
        surfaceUniforms.beginFrame();
        renderFrameCounter++;
        renderersSeenCount = 0;
        memoPipelineMaterial = null;
        memoPipelineClassResources = null;
        materialChangedCache.clear();
        camera.position(scratchCameraPosition, alpha);
        culler.setProjection(camera.cullingViewProjection(alpha));
        culledThisFrame = 0;
        submittedThisFrame = 0;
        instanceBatches.beginFrame();
        beginMultiDrawGroups();
        boundsCacheHits = 0;
        boundsCacheMisses = 0;
        activeCullMask = camera.cullMask();
        sceneRelevance.beginFrame(lastCameraViewProjection, shadowCascades, activeLights,
                spotShadows.activeCount() > 0 || pointShadows.activeCount() > 0);
        if (skinnedDeformer != null) {
            skinnedDeformer.beginFrame();
        }
        updateAnimationAndSockets(scene, alpha, context.animationGeneration());
        submitIndexedMeshDraws(scene, frame, alpha);
        mark = markSection("mesh/objectLoop", mark);
        for (MultiMeshRenderer renderer : scene.componentsOf(MultiMeshRenderer.class)) {
            submitMultiMeshDraws(renderer);
        }
        mark = markSection("mesh/instancedLoop", mark);
        flushInstanceBatches(frame);
        flushMultiDrawGroups(frame);
        if (skinnedDeformer != null) {
            skinnedDeformer.flush();
        }
        environment.collectSky(camera, scratchSunDirection, frame, alpha);
        purgeOrphanRenderers();
        purgeOrphanBounds();
        markSection("mesh/flush", mark);
        reportLoopProfile();
    }

    public Environment environment() {
        return environment;
    }

    public float shadowDistance() {
        return shadowDistance;
    }

    public MeshRenderSystem setShadowDistance(float distance) {
        this.shadowDistance = distance;
        return this;
    }

    private void refreshProbeLighting(Scene scene) {
        activeProbes = gatherProbes(scene);
        if (probeGrid.update(activeProbes)) {
            invalidateObjectResources();
        }
        materialCache.setProbeLightingActive(activeProbes.isPresent());
    }

    private void submitIndirectInstancedBatch(FrameBuilder frame, MeshInstanceBatch batch,
                                              PipelineHandle pipeline) {
        GpuCullResources resources = cullResourcesFor(batch);
        BindingSetHandle bindings = indirectBindings.get(batch);
        if (bindings == null) {
            return;
        }
        frame.submit(RenderPasses.OPAQUE_3D, DrawCommand.indirect(pipeline, batch.submesh().handle(),
                bindings, pipeline.id() << 32, resources.indirectArguments()));
    }

    private boolean gpuCullable(MeshInstanceBatch batch) {
        return gpuCullingEnabled && depthPyramid.texture() != null && batch.localBounds() != null
                && !batch.representative().material().blended()
                && batch.instanceCount() >= gpuCullMinimumInstances;
    }

    private GpuCullResources cullResourcesFor(MeshInstanceBatch batch) {
        GpuCullResources existing = cullResources.get(batch);
        if (existing != null && existing.capacity() >= batch.instanceCount()
                && existing.pyramid() == depthPyramid.texture()
                && existing.source() == batch.instanceBuffer()) {
            return existing;
        }
        if (existing != null) {
            existing.destroy(backend);
            backend.destroy(indirectBindings.remove(batch));
        }
        GpuCullResources created = GpuCullResources.create(backend, batch.instanceBuffer(),
                depthPyramid.texture(), Math.max(1, batch.instanceCount()));
        cullResources.put(batch, created);
        indirectBindings.put(batch, createInstanceBindingSet(batch.representative(),
                created.visibleInstances(), 0L,
                (long) created.capacity() * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES, false));
        return created;
    }

    private void runGpuCulling() {
        if (!gpuCullingEnabled || depthPyramid.texture() == null) {
            return;
        }
        for (MeshInstanceBatch batch : instanceBatches.activeBatches()) {
            if (!gpuCullable(batch)) {
                continue;
            }
            GpuCullResources resources = cullResources.get(batch);
            if (resources == null) {
                continue;
            }
            dispatchCullPhases(batch, resources);
        }
    }

    private void dispatchCullPhases(MeshInstanceBatch batch, GpuCullResources resources) {
        MeshHandle mesh = batch.submesh().handle();
        resources.resetIndirectArguments(backend, backend.meshIndexCount(mesh), backend.meshFirstIndex(mesh));
        writeCullParameters(batch, resources, GpuInstanceCuller.PHASE_REDRAW_LAST_VISIBLE);
        backend.dispatchCompute(ComputeDispatch.of(instanceCuller.pipeline(), resources.cullBindings(),
                GpuInstanceCuller.groupCountFor(batch.instanceCount())));
        backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        writeCullParameters(batch, resources, GpuInstanceCuller.PHASE_TEST_REMAINDER);
        backend.dispatchCompute(ComputeDispatch.of(instanceCuller.pipeline(), resources.cullBindings(),
                GpuInstanceCuller.groupCountFor(batch.instanceCount())));
        backend.computeBarrier(ComputeBarrier.ALL);
        if (Boolean.getBoolean("epysia.render.gpuCullDebug")) {
            java.nio.ByteBuffer readback = BufferUtils.createByteBuffer(
                    GpuInstanceCuller.INDIRECT_ARGUMENT_BYTES);
            backend.readBuffer(resources.indirectArguments(), readback, 0L);
        }
    }

    private void writeCullParameters(MeshInstanceBatch batch, GpuCullResources resources, int phase) {
        scratchCullParameters.clear();
        lastCameraViewProjection.get(scratchCullParameters);
        scratchCullParameters.position(64);
        writeFrustumPlanes(scratchCullParameters);
        Aabb bounds = batch.localBounds();
        scratchCullParameters.putFloat(bounds.minX()).putFloat(bounds.minY())
                .putFloat(bounds.minZ()).putFloat(0.0f);
        scratchCullParameters.putFloat(bounds.maxX()).putFloat(bounds.maxY())
                .putFloat(bounds.maxZ()).putFloat(0.0f);
        scratchCullParameters.putFloat(backend.textureWidth(depthPyramid.texture()))
                .putFloat(backend.textureHeight(depthPyramid.texture()))
                .putFloat(depthPyramid.levels()).putFloat(0.0f);
        scratchCullParameters.putInt(batch.instanceCount()).putInt(phase).putInt(0).putInt(0);
        scratchCullParameters.flip();
        backend.writeBuffer(resources.parameters(), scratchCullParameters, 0L);
    }

    private void writeFrustumPlanes(ByteBuffer target) {
        for (int plane = 0; plane < 6; plane++) {
            lastCameraViewProjection.frustumPlane(plane, scratchFrustumPlane);
            target.putFloat(scratchFrustumPlane.x).putFloat(scratchFrustumPlane.y)
                    .putFloat(scratchFrustumPlane.z).putFloat(scratchFrustumPlane.w);
        }
    }

    private void refreshDepthPyramid() {
        if (!gpuCullingEnabled || stageConfigurer == null) {
            return;
        }
        TextureHandle depth = stageConfigurer.sceneTexture(SceneTexture.SCENE_DEPTH).orElse(null);
        if (depth == null) {
            return;
        }
        if (depth != pyramidSourceDepth) {
            pyramidSourceDepth = depth;
            depthPyramid.invalidate();
        }
        int width = backend.textureWidth(depth);
        int height = backend.textureHeight(depth);
        if (width <= 0 || height <= 0) {
            return;
        }
        depthPyramid.resize(depth, width, height);
        depthPyramid.build();
        runGpuCulling();
        occlusionGrid.refresh(backend, depthPyramid, lastCameraViewProjection);
    }

    TextureHandle shadowCascadeTexture() {
        return shadowCascades.texture();
    }

    public TextureHandle opaqueDepthTexture() {
        return opaqueSceneTexture(SceneTexture.OPAQUE_DEPTH);
    }

    private TextureHandle opaqueSceneTexture(SceneTexture slot) {
        if (stageConfigurer == null) {
            return shadowCascades.texture();
        }
        return stageConfigurer.sceneTexture(slot).orElseGet(shadowCascades::texture);
    }

    private void refreshSceneTextureBindings() {
        TextureHandle color = opaqueSceneTexture(SceneTexture.OPAQUE_COLOR);
        if (color.equals(lastOpaqueColorTexture)) {
            return;
        }
        lastOpaqueColorTexture = color;
        invalidateObjectResources();
    }

    private void applySkybox(Scene scene) {
        for (Skybox skybox : scene.componentsOf(Skybox.class)) {
            environment.setSource(skybox.source());
            applySkyboxIntensities(skybox.skyIntensity(), skybox.ambientIntensity());
            return;
        }
        environment.setSource(SkySource.PROCEDURAL);
        appliedSkyIntensity = Float.NaN;
        appliedAmbientIntensity = Float.NaN;
    }

    private void applySkyboxIntensities(float sky, float ambient) {
        if (sky == appliedSkyIntensity && ambient == appliedAmbientIntensity) {
            return;
        }
        appliedSkyIntensity = sky;
        appliedAmbientIntensity = ambient;
        environment.settings().setSkyIntensity(sky);
        environment.settings().setAmbientIntensity(ambient);
    }

    private static Optional<BakedProbes> gatherProbes(Scene scene) {
        for (LightProbeVolume volume : scene.componentsOf(LightProbeVolume.class)) {
            if (volume.bakedProbes().isPresent()) {
                return volume.bakedProbes();
            }
        }
        return Optional.empty();
    }

    private void invalidateObjectResources() {
        for (RenderableMesh renderable : objectResources.values()) {
            destroyRenderable(renderable);
        }
        objectResources.clear();
        renderableGeneration++;
        instanceBatches.shutdown();
    }

    private void resolveSunDirection(Optional<DirectionalLight> primaryDirectional) {
        if (primaryDirectional.isPresent()) {
            primaryDirectional.get().direction(scratchSunDirection).negate().normalize();
        } else {
            scratchSunDirection.set(environment.defaultSunDirection());
        }
    }

    public int culledMeshCount() {
        return culledThisFrame;
    }

    public int submittedMeshCount() {
        return submittedThisFrame;
    }

    public ShadowStatistics shadowStatistics() {
        return shadowStatistics;
    }

    public void setShadowCachingEnabled(boolean enabled) {
        this.shadowCachingEnabled = enabled;
        shadowCascades.setCachingEnabled(enabled);
        spotShadows.setCachingEnabled(enabled);
        pointShadows.setCachingEnabled(enabled);
        applyShadowSplit();
    }

    public boolean shadowCachingEnabled() {
        return shadowCachingEnabled;
    }

    public void setShadowSplitEnabled(boolean enabled) {
        this.shadowSplitEnabled = enabled;
        applyShadowSplit();
    }

    private void applyShadowSplit() {
        boolean effective = shadowSplitEnabled && shadowCachingEnabled;
        shadowCascades.setSplitEnabled(effective);
        spotShadows.setSplitEnabled(effective);
        pointShadows.setSplitEnabled(effective);
    }

    public boolean shadowSplitEnabled() {
        return shadowSplitEnabled;
    }

    public long shadowStaticVideoMemoryBytes() {
        return shadowCascades.staticVideoMemoryBytes()
                + spotShadows.staticVideoMemoryBytes()
                + pointShadows.staticVideoMemoryBytes();
    }

    private PipelineHandle createDepthPrepassPipeline() {
        VertexLayout layout = new VertexLayout(
                List.of(new VertexAttribute(0, VertexFormat.FLOAT3, 0)), MeshShaderBindings.VERTEX_STRIDE);
        return backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(shaderLoader.load(DEPTH_PREPASS_VERTEX_PATH).source(),
                        shaderLoader.load(DEPTH_PREPASS_FRAGMENT_PATH).source()),
                layout, RenderState.OPAQUE_3D.withoutColorWrite(), shadowCascades.bindingLayout()));
    }

    private PipelineHandle createMaskedDepthPrepassPipeline() {
        VertexLayout layout = new VertexLayout(List.of(
                new VertexAttribute(0, VertexFormat.FLOAT3, 0),
                new VertexAttribute(1, VertexFormat.FLOAT3, 12),
                new VertexAttribute(2, VertexFormat.FLOAT2, 24)), MeshShaderBindings.VERTEX_STRIDE);
        return backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(shaderLoader.load(MASKED_DEPTH_PREPASS_VERTEX_PATH).source(),
                        shaderLoader.load(MASKED_DEPTH_PREPASS_FRAGMENT_PATH).source()),
                layout, RenderState.OPAQUE_3D.withoutColorWrite(), shadowCascades.maskedBindingLayout()));
    }

    public void applyTuning(RenderTuning tuning) {
        gpuCullingEnabled = tuning.gpuCulling();
        multiDrawEnabled = tuning.multiDraw();
        setSceneIndexEnabled(tuning.sceneIndex());
        setInstancingEnabled(tuning.instancing());
        setPipelineMemoEnabled(tuning.pipelineMemo());
        setTransformLookupCached(tuning.cachedTransformLookup());
        setSharedMaterialDigest(tuning.sharedMaterialDigest());
        setSkinOnceEnabled(tuning.skinOnce());
        setAnimationCullingEnabled(tuning.animationCulling());
        setAnimationFullRateDistance(tuning.animationFullRateDistance());
        setGpuCullMinimumInstances(tuning.gpuCullMinimumInstances());
        setFrontToBackOpaque(tuning.frontToBackOpaque());
        setShadowLayerReuse(tuning.shadowLayerReuse());
        setRingInstanceBuffers(tuning.ringInstanceBuffers());
        setRingObjectUniforms(tuning.ringObjectUniforms());
        setParallelAnimation(tuning.parallelAnimation());
    }

    public void setRingInstanceBuffers(boolean value) {
        instanceBatches.setPerFrameBuffers(value);
    }

    public boolean ringInstanceBuffers() {
        return instanceBatches.perFrameBuffers();
    }

    public void setShadowLayerReuse(boolean value) {
        shadowCascades.setStaticLayerReuse(value);
    }

    public boolean shadowLayerReuse() {
        return shadowCascades.staticLayerReuse();
    }

    public void setFrontToBackOpaque(boolean value) {
        frontToBackOpaque = value;
    }

    public boolean frontToBackOpaque() {
        return frontToBackOpaque;
    }

    public void setParallelAnimation(boolean value) {
        parallelAnimation = value;
    }

    public boolean parallelAnimation() {
        return parallelAnimation;
    }

    public void setSkinOnceEnabled(boolean value) {
        if (value == skinOnceEnabled) {
            return;
        }
        skinOnceEnabled = value;
        invalidateObjectResources();
    }

    public boolean skinOnceEnabled() {
        return skinOnceEnabled;
    }

    public boolean gpuCullingEnabled() {
        return gpuCullingEnabled;
    }

    public boolean multiDrawEnabled() {
        return multiDrawEnabled;
    }

    public void setDepthPrepassEnabled(boolean enabled) {
        this.depthPrepassEnabled = enabled;
    }

    public boolean depthPrepassEnabled() {
        return depthPrepassEnabled;
    }

    private void submitDepthPrepass(FrameBuilder frame, UploadedSubmesh submesh, BindingSetHandle bindings,
                                    Material material, long depthBits, int instanceCount, boolean skinned,
                                    boolean colored, boolean masked) {
        if (!depthPrepassEnabled || skinned || material.blended()) {
            return;
        }
        if (material.alphaScissor() && !masked) {
            return;
        }
        SurfaceShadowVariants variants = masked ? maskedDepthPrepassVariants : depthPrepassVariants;
        PipelineHandle pipeline = variants.pipelineFor(
                MaterialPipelineCache.surfaceShaderPathOf(material), false, false, colored,
                material.doubleSided());
        frame.submit(RenderPasses.OPAQUE_3D, new DrawCommand(pipeline, submesh.handle(), bindings,
                depthBits, instanceCount));
    }

    public void setInstancingEnabled(boolean enabled) {
        this.instancingEnabled = enabled;
        invalidateShadowCaches();
    }

    public boolean instancingEnabled() {
        return instancingEnabled;
    }

    public int batchCount() {
        return batchesThisFrame;
    }

    public int instancedBatchCount() {
        return instancedBatchesThisFrame;
    }

    private void invalidateShadowCaches() {
        surfaceTimeDependence.clear();
        shadowCascades.invalidateCache();
        spotShadows.invalidateCache();
        pointShadows.invalidateCache();
    }

    public void setClusteringEnabled(boolean enabled) {
        this.clusteringEnabled = enabled;
    }

    public boolean clusteringEnabled() {
        return clusteringEnabled;
    }

    @Override
    public void shutdown(RenderBackend backend) {
        if (frameWorkers != null) {
            frameWorkers.shutdown();
            frameWorkers = null;
        }
        for (BindingSetHandle binding : ownedBindings) {
            backend.destroy(binding);
        }
        for (BufferHandle buffer : ownedBuffers) {
            backend.destroy(buffer);
        }
        ownedBindings.clear();
        ownedBuffers.clear();
        if (objectUniforms != null) {
            objectUniforms.destroy();
            objectUniforms = null;
        }
        objectResources.clear();
        renderableGeneration++;
        renderFrameCounter++;
        renderersSeenCount = 0;
        materialChangedCache.clear();
        instanceBatches.shutdown();
        depthPrepassVariants.shutdown();
        maskedDepthPrepassVariants.shutdown();
        surfaceUniforms.shutdown();
        materialCache.shutdown();
        shadowCascades.shutdown();
        spotShadows.shutdown();
        pointShadows.shutdown();
        frameUboWriter.shutdown();
        lightStorage.shutdown();
        probeGrid.shutdown();
        clusterCuller.shutdown();
        environment.shutdown();
    }

    private Optional<DirectionalLight> gatherLights(Scene scene) {
        scratchDirectionalLights.clear();
        scratchOtherLights.clear();
        for (Light light : scene.componentsOf(Light.class)) {
            classifyLight(light);
        }
        List<DirectionalLight> directionals = scene.componentsOf(DirectionalLight.class);
        Optional<DirectionalLight> primaryDirectional = directionals.isEmpty()
                ? Optional.empty()
                : Optional.of(directionals.getFirst());
        assembleActiveLights(primaryDirectional);
        return primaryDirectional;
    }

    private void classifyLight(Light light) {
        if (light instanceof DirectionalLight) {
            scratchDirectionalLights.add(light);
        } else {
            scratchOtherLights.add(light);
        }
    }

    private void assembleActiveLights(Optional<DirectionalLight> primary) {
        activeLights.clear();
        primary.ifPresent(activeLights::add);
        appendLights(scratchDirectionalLights, primary);
        appendLights(scratchOtherLights, primary);
    }

    private void appendLights(List<Light> lights, Optional<DirectionalLight> primary) {
        for (Light light : lights) {
            if (primary.isPresent() && light == primary.get()) {
                continue;
            }
            if (activeLights.size() < LightStorage.MAX_LIGHTS) {
                activeLights.add(light);
            }
        }
    }

    private void assignSpotShadows() {
        for (Light light : activeLights) {
            if (!(light instanceof SpotLight spot) || !spot.castShadows()) {
                continue;
            }
            if (spotShadows.activeCount() >= MeshShaderBindings.MAX_SHADOW_SPOTS) {
                return;
            }
            spotShadows.assign(spot, computeSpotMatrix(spot));
        }
    }

    private Matrix4f computeSpotMatrix(SpotLight spot) {
        spot.position(scratchSpotPosition);
        spot.direction(scratchSpotDirection).normalize();
        scratchSpotTarget.set(scratchSpotPosition).add(scratchSpotDirection);
        chooseSpotUp(scratchSpotDirection);
        float halfAngle = (float) Math.acos(clampCosine(spot.outerConeCosine()));
        float fieldOfView = Math.min(2.0f * halfAngle + 0.12f, 3.0f);
        float farPlane = Math.max(spot.range(), 0.2f);
        scratchSpotMatrix.setPerspective(fieldOfView, 1.0f, 0.05f, farPlane);
        scratchSpotMatrix.lookAt(scratchSpotPosition, scratchSpotTarget, scratchSpotUp);
        return scratchSpotMatrix;
    }

    private void chooseSpotUp(Vector3f direction) {
        if (Math.abs(direction.y) > 0.99f) {
            scratchSpotUp.set(0.0f, 0.0f, 1.0f);
        } else {
            scratchSpotUp.set(0.0f, 1.0f, 0.0f);
        }
    }

    private static float clampCosine(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private void assignPointShadows() {
        for (Light light : activeLights) {
            if (!(light instanceof PointLight point) || !point.castShadows()) {
                continue;
            }
            if (pointShadows.activeCount() >= MeshShaderBindings.MAX_SHADOW_POINTS) {
                return;
            }
            computePointFaceMatrices(point);
            pointShadows.assign(point, scratchPointFaces);
        }
    }

    private void computePointFaceMatrices(PointLight point) {
        point.position(scratchPointPosition);
        float farPlane = Math.max(point.range(), 0.2f);
        for (int face = 0; face < MeshShaderBindings.POINT_SHADOW_FACES; face++) {
            scratchPointTarget.set(scratchPointPosition).add(CUBE_DIRECTIONS[face]);
            scratchPointFaces[face]
                    .setPerspective((float) (Math.PI / 2.0), 1.0f, 0.05f, farPlane)
                    .lookAt(scratchPointPosition, scratchPointTarget, CUBE_UPS[face]);
        }
    }

    private static Matrix4f[] createPointFaceMatrices() {
        Matrix4f[] faces = new Matrix4f[MeshShaderBindings.POINT_SHADOW_FACES];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = new Matrix4f();
        }
        return faces;
    }

    private void updateAnimationAndSockets(Scene scene, float alpha, long generation) {
        if (generation == animationGeneration) {
            return;
        }
        animationGeneration = generation;
        poseSchedule.beginFrame();
        animatedPalettes.clear();
        pendingPoseCount = 0;
        for (MeshRenderer renderer : scene.componentsOf(MeshRenderer.class)) {
            collectAnimatedPalette(renderer, alpha);
        }
        samplePendingPoses();
        publishPendingPoses();
        if (animatedPalettes.isEmpty()) {
            return;
        }
        for (JointSocket socket : scene.componentsOf(JointSocket.class)) {
            applyJointSocket(socket);
        }
    }

    private void collectAnimatedPalette(MeshRenderer renderer, float alpha) {
        UploadedMesh mesh = renderer.meshOrNull();
        GameObject owner = renderer.ownerOrNull();
        if (mesh == null || !mesh.skinned() || owner == null) {
            return;
        }
        RenderableMesh renderable = objectResources.get(renderer);
        JointPalette palette = renderable == null ? null : renderable.jointPaletteOrNull();
        if (palette == null) {
            return;
        }
        animatedPalettes.put(owner, palette);
        updateAnimatedPalette(owner, mesh, renderable, alpha, castsShadows(renderer));
    }

    private void applyJointSocket(JointSocket socket) {
        GameObject rig = rigOf(socket);
        if (rig == null) {
            return;
        }
        MeshRenderer renderer = rig.getComponentOrNull(MeshRenderer.class);
        UploadedMesh mesh = renderer == null ? null : renderer.meshOrNull();
        if (mesh == null || mesh.skeleton().isEmpty()) {
            return;
        }
        if (socket.resolve(mesh.skeleton().orElseThrow())) {
            socket.applyPose(animatedPalettes.get(rig).pose);
        }
    }

    private GameObject rigOf(JointSocket socket) {
        GameObject owner = socket.ownerOrNull();
        Transform3D transform = owner == null ? null : owner.transform3DOrNull();
        Optional<Transform3D> parent = transform == null ? Optional.empty() : transform.parent();
        while (parent.isPresent()) {
            GameObject candidate = parent.orElseThrow().ownerOrNull();
            if (candidate != null && animatedPalettes.containsKey(candidate)) {
                return candidate;
            }
            parent = parent.orElseThrow().parent();
        }
        return null;
    }

    private void submitIndexedMeshDraws(Scene scene, FrameBuilder frame, float alpha) {
        List<MeshRenderer> renderers = scene.componentsOf(MeshRenderer.class);
        skippedByIndexThisFrame = 0;
        skippedByRelevanceThisFrame = 0;
        if (!sceneIndexEnabled || indexBypassed) {
            for (MeshRenderer renderer : renderers) {
                submitMeshDraws(renderer, frame, alpha);
            }
            if (sceneIndexEnabled) {
                advanceIndexBypass();
            }
            return;
        }
        indexAlpha = alpha;
        occludedThisFrame = 0;
        unindexedRenderers.clear();
        ensurePreparedCapacity(renderers.size());
        sceneRenderIndex.refresh(renderers, this::indexWorldBounds, scene.modificationCount());
        for (MeshRenderer renderer : unindexedRenderers) {
            submitMeshDraws(renderer, frame, alpha);
        }
        sceneRenderIndex.query(sceneRelevance,
                slot -> submitPreparedMeshDraws(renderers.get(slot), preparedBySlot[slot], frame, alpha));
        skippedByIndexThisFrame = sceneRenderIndex.entryCount() - sceneRenderIndex.candidateCount();
        updateIndexActivation();
    }

    private void reportLoopProfile() {
        if (!LOOP_PROFILING) {
            return;
        }
        loopProfileFrames++;
        if (loopProfileFrames < 120) {
            return;
        }
        logger.info(String.format("[loop] bounds %.3f | resolve %.3f | material %.3f | submit %.3f ms/frame",
                phaseBoundsNanos / 120.0 / 1.0e6, phaseResolveNanos / 120.0 / 1.0e6,
                phaseMaterialNanos / 120.0 / 1.0e6, phaseSubmitNanos / 120.0 / 1.0e6));
        phaseBoundsNanos = 0L;
        phaseResolveNanos = 0L;
        phaseMaterialNanos = 0L;
        phaseSubmitNanos = 0L;
        loopProfileFrames = 0;
    }

    private void advanceIndexBypass() {
        indexBypassFrames--;
        if (indexBypassFrames <= 0) {
            indexBypassed = false;
            indexIdleFrames = 0;
        }
    }

    private void updateIndexActivation() {
        int entries = sceneRenderIndex.entryCount();
        float prunedFraction = entries == 0 ? 1.0f : (float) skippedByIndexThisFrame / entries;
        if (prunedFraction >= INDEX_MINIMUM_PRUNED_FRACTION) {
            indexIdleFrames = 0;
            return;
        }
        indexIdleFrames++;
        if (indexIdleFrames >= INDEX_IDLE_FRAMES_BEFORE_BYPASS) {
            indexBypassed = true;
            indexBypassFrames = INDEX_BYPASS_FRAMES;
        }
    }

    private boolean indexWorldBounds(int slot, MeshRenderer renderer,
                                     Vector3f outMinimum, Vector3f outMaximum) {
        if (!RenderLayers.intersects(renderer.layerMask(), activeCullMask)) {
            return false;
        }
        GameObject gameObject = renderer.ownerOrNull();
        if (gameObject == null) {
            return false;
        }
        Transform3D transform = transformOf(gameObject);
        UploadedMesh mesh = renderer.meshOrNull();
        if (transform == null || mesh == null) {
            return false;
        }
        Aabb localBounds = indexBoundsOf(renderer, mesh);
        if (localBounds == null) {
            unindexedRenderers.add(renderer);
            return false;
        }
        long boundsStart = LOOP_PROFILING ? System.nanoTime() : 0L;
        Matrix4f modelMatrix = transform.worldMatrix(indexAlpha);
        computeCachedWorldBounds(renderer, transform, localBounds, modelMatrix, indexAlpha);
        storePreparedEntry(slot, renderer, gameObject, transform, mesh, modelMatrix);
        if (LOOP_PROFILING) {
            phaseBoundsNanos += System.nanoTime() - boundsStart;
        }
        outMinimum.set(scratchCasterMin);
        outMaximum.set(scratchCasterMax);
        return true;
    }

    private Aabb indexBoundsOf(MeshRenderer renderer, UploadedMesh mesh) {
        if (!mesh.skinned()) {
            return mesh.localBounds();
        }
        RenderableMesh renderable = objectResources.get(renderer);
        return renderable == null ? mesh.localBounds() : cullBounds(mesh, renderable);
    }

    private void storePreparedEntry(int slot, MeshRenderer renderer, GameObject gameObject,
                                    Transform3D transform, UploadedMesh mesh, Matrix4f modelMatrix) {
        PreparedEntry entry = preparedBySlot[slot];
        if (entry == null) {
            entry = new PreparedEntry();
            preparedBySlot[slot] = entry;
        }
        if (entry.renderer != renderer || entry.mesh != mesh) {
            entry.renderable = null;
        }
        entry.renderer = renderer;
        entry.gameObject = gameObject;
        entry.transform = transform;
        entry.mesh = mesh;
        entry.modelMatrix.set(modelMatrix);
        entry.worldMinimum.set(scratchCasterMin);
        entry.worldMaximum.set(scratchCasterMax);
    }

    private void ensurePreparedCapacity(int renderers) {
        if (preparedBySlot.length < renderers) {
            preparedBySlot = Arrays.copyOf(preparedBySlot, renderers);
        }
    }

    private static final class PreparedEntry {
        private MeshRenderer renderer;
        private RenderableMesh renderable;
        private int renderableGeneration = -1;
        private GameObject gameObject;
        private Transform3D transform;
        private UploadedMesh mesh;
        private final Matrix4f modelMatrix = new Matrix4f();
        private final Vector3f worldMinimum = new Vector3f();
        private final Vector3f worldMaximum = new Vector3f();
    }

    public void setSharedMaterialDigest(boolean value) {
        materialStates.setSharedRevision(value);
    }

    public void setSceneIndexEnabled(boolean value) {
        sceneIndexEnabled = value;
    }

    public boolean sceneIndexEnabled() {
        return sceneIndexEnabled;
    }

    public int skippedByIndexThisFrame() {
        return skippedByIndexThisFrame;
    }

    public int skippedByRelevanceThisFrame() {
        return skippedByRelevanceThisFrame;
    }

    public int indexedEntryCount() {
        return sceneRenderIndex.entryCount();
    }

    private void submitMeshDraws(MeshRenderer renderer, FrameBuilder frame, float alpha) {
        if (!RenderLayers.intersects(renderer.layerMask(), activeCullMask)) {
            return;
        }
        GameObject gameObject = renderer.ownerOrNull();
        if (gameObject == null) {
            return;
        }
        Transform3D transformComponent = transformOf(gameObject);
        UploadedMesh mesh = renderer.meshOrNull();
        if (transformComponent == null || mesh == null) {
            return;
        }
        submitResolvedMeshDraws(renderer, gameObject, transformComponent, mesh,
                transformComponent.worldMatrix(alpha), frame, alpha, false, null);
    }

    private void submitPreparedMeshDraws(MeshRenderer renderer, PreparedEntry prepared,
                                         FrameBuilder frame, float alpha) {
        if (irrelevantWithoutShadows(renderer, prepared)) {
            culledThisFrame++;
            skippedByRelevanceThisFrame++;
            return;
        }
        scratchCasterMin.set(prepared.worldMinimum);
        scratchCasterMax.set(prepared.worldMaximum);
        if (!renderer.castsShadows() && occlusionGrid.isOccluded(
                prepared.worldMinimum.x, prepared.worldMinimum.y, prepared.worldMinimum.z,
                prepared.worldMaximum.x, prepared.worldMaximum.y, prepared.worldMaximum.z)) {
            occludedThisFrame++;
            return;
        }
        submitResolvedMeshDraws(renderer, prepared.gameObject, prepared.transform, prepared.mesh,
                prepared.modelMatrix, frame, alpha, true, prepared);
    }

    private boolean irrelevantWithoutShadows(MeshRenderer renderer, PreparedEntry prepared) {
        if (renderer.castsShadows() && !renderer.viewModel()) {
            return false;
        }
        return !sceneRelevance.overlaps(false,
                prepared.worldMinimum.x, prepared.worldMinimum.y, prepared.worldMinimum.z,
                prepared.worldMaximum.x, prepared.worldMaximum.y, prepared.worldMaximum.z);
    }

    private void submitResolvedMeshDraws(MeshRenderer renderer, GameObject gameObject,
                                         Transform3D transformComponent, UploadedMesh baseMesh,
                                         Matrix4f modelMatrix, FrameBuilder frame, float alpha,
                                         boolean boundsReady, PreparedEntry prepared) {
        if (!transformComponent.visible()) {
            return;
        }
        UploadedMesh mesh = selectLevelOfDetail(renderer, baseMesh, modelMatrix);
        boolean viewModel = renderer.viewModel();
        boolean castsShadows = renderer.castsShadows() && !viewModel;
        long resolveStart = LOOP_PROFILING ? System.nanoTime() : 0L;
        RenderableMesh renderable = cachedRenderableOf(prepared, mesh);
        if (renderable == null) {
            renderable = resolvePerSubmeshes(renderer, mesh);
            if (prepared != null) {
                prepared.renderable = renderable;
                prepared.renderableGeneration = renderableGeneration;
            }
        }
        markSeen(renderable);
        List<PerSubmesh> perSubmeshes = renderable.submeshes();
        refreshStalePerSubmeshes(renderer, mesh, renderable);
        if (LOOP_PROFILING) {
            phaseResolveNanos += System.nanoTime() - resolveStart;
        }
        JointPalette palette = renderable.jointPaletteOrNull();
        if (mesh.skinned() && palette != null) {
            updateAnimatedPalette(gameObject, mesh, renderable, alpha, castsShadows(renderer));
        }
        deformIfStale(renderable);
        Aabb cullBounds = cullBounds(mesh, renderable);
        if (!boundsReady) {
            computeCachedWorldBounds(renderer, transformComponent, cullBounds, modelMatrix, alpha);
        }
        boolean visible = cullBounds == null
                || !culler.isCulled(scratchCasterMin, scratchCasterMax);
        if (outOfVisibilityRange(renderer, scratchCasterMin, scratchCasterMax)) {
            culledThisFrame++;
            return;
        }
        if (!visible && !castsShadows) {
            culledThisFrame++;
            skippedByRelevanceThisFrame++;
            return;
        }
        long depthBits = viewDepthBits(modelMatrix);
        long transformHash = ShadowSignatures.mixMatrix(ShadowSignatures.seed(), modelMatrix);
        if (visible) {
            submittedThisFrame++;
        } else {
            culledThisFrame++;
        }
        for (int i = 0; i < mesh.submeshes().size(); i++) {
            UploadedSubmesh submesh = renderable.drawSubmesh(i);
            PerSubmesh perSubmesh = perSubmeshes.get(i);
            long materialStart = LOOP_PROFILING ? System.nanoTime() : 0L;
            materialCache.writeMaterialUboIfNeeded(perSubmesh.material(), perSubmesh.classResources());
            surfaceUniforms.writeIfNeeded(perSubmesh.material(), perSubmesh.classResources().surfaceUniforms());
            if (LOOP_PROFILING) {
                phaseMaterialNanos += System.nanoTime() - materialStart;
            }
            if (!viewModel && !mesh.skinned() && !depthPrepassEnabled
                    && mesh.arenaBacked() && !perSubmesh.material().blended() && visible
                    && addToMultiDrawGroup(mesh, submesh, perSubmesh, modelMatrix, depthBits)) {
                if (castsShadows) {
                    writeObjectUboIfChanged(perSubmesh.modelSlot(), modelMatrix, transformHash);
                    submitShadowCasters(submesh, perSubmesh,
                            MaterialPipelineCache.surfaceShaderPathOf(perSubmesh.material()),
                            transformHash, false, false, mesh.vertexColored());
                }
                continue;
            }
            if (!viewModel && !mesh.skinned() && batchable(perSubmesh)
                    && instanceBatches.add(submesh, perSubmesh,
                            materialStates.snapshotFor(perSubmesh, materialCache, surfaceUniforms),
                            modelMatrix, depthBits, visible, castsShadows, mesh.vertexColored(),
                            scratchCasterMin, scratchCasterMax, cullBounds)) {
                continue;
            }
            writeObjectUboIfChanged(perSubmesh.modelSlot(), modelMatrix, transformHash);
            submitSubmesh(frame, submesh, perSubmesh, depthBits, transformHash, visible,
                    !castsShadows, renderable.skinnedPipelines(), mesh.skinned(),
                    mesh.vertexColored(), viewModel);
        }
    }

    private void submitMultiMeshDraws(MultiMeshRenderer renderer) {
        if (!RenderLayers.intersects(renderer.layerMask(), activeCullMask)) {
            return;
        }
        UploadedMesh baseMesh = renderer.meshOrNull();
        int count = renderer.visibleInstanceCount();
        if (baseMesh == null || baseMesh.skinned() || count == 0 || renderer.materialOrNull() == null) {
            return;
        }
        MeshInstanceBatches.BulkInstances bulk = instanceBatches.bulkFor(renderer, baseMesh.submeshes().size());
        if (!bulk.tilesMatch(renderer.dataRevision(), renderer.instanceCount())) {
            bulk.rebuildTiles(renderer.instanceData(), renderer.instanceCount(), renderer.dataRevision(),
                    baseMesh.localBounds());
        }
        submitBulkInstances(renderer, baseMesh, bulk, count);
    }

    private void submitBulkInstances(MultiMeshRenderer renderer, UploadedMesh baseMesh,
                                     MeshInstanceBatches.BulkInstances bulk, int count) {
        UploadedMesh mesh = selectLevelOfDetail(renderer, baseMesh, bulk.boundsMin(), bulk.boundsMax());
        RenderableMesh renderable = resolvePerSubmeshes(renderer, mesh);
        markSeen(renderable);
        refreshStalePerSubmeshes(renderer, mesh, renderable);
        if (outOfVisibilityRange(renderer, bulk.boundsMin(), bulk.boundsMax())) {
            culledThisFrame++;
            return;
        }
        boolean unbounded = baseMesh.localBounds() == null;
        boolean visible = unbounded || !culler.isCulled(bulk.boundsMin(), bulk.boundsMax());
        long depthBits = unbounded ? 0L : bulkDepthBits(bulk);
        for (int slot = 0; slot < mesh.submeshes().size(); slot++) {
            submitInstancedSubmesh(bulk, slot, mesh, renderable.submeshes().get(slot), renderer, count,
                    visible, depthBits);
        }
        submittedThisFrame += visible ? 1 : 0;
        culledThisFrame += visible ? 0 : 1;
    }

    private long bulkDepthBits(MeshInstanceBatches.BulkInstances bulk) {
        return viewDepthBits(
                (bulk.boundsMin().x + bulk.boundsMax().x) * 0.5f,
                (bulk.boundsMin().y + bulk.boundsMax().y) * 0.5f,
                (bulk.boundsMin().z + bulk.boundsMax().z) * 0.5f);
    }

    private void submitInstancedSubmesh(MeshInstanceBatches.BulkInstances bulk, int slot, UploadedMesh mesh,
                                        PerSubmesh perSubmesh, MultiMeshRenderer renderer, int count,
                                        boolean visible, long depthBits) {
        materialCache.writeMaterialUboIfNeeded(perSubmesh.material(), perSubmesh.classResources());
        surfaceUniforms.writeIfNeeded(perSubmesh.material(), perSubmesh.classResources().surfaceUniforms());
        MeshInstanceBatch batch = bulk.batch(slot);
        batch.beginFrame();
        batch.beginBulk(mesh.submeshes().get(slot), perSubmesh);
        batch.setCastsShadows(renderer.castsShadows());
        batch.setVertexColored(mesh.vertexColored());
        batch.setVisibilityRange(renderer.visibilityRangeBegin(), renderer.visibilityRangeEnd());
        batch.adoptTiles(bulk.tiles().tileStart(), bulk.tiles().tileLength(), bulk.tiles().tileBounds());
        batch.writeBulkIfStale(bulk.tiles().payload(), renderer.instanceCount(), renderer.dataRevision());
        batch.adoptBulkCounts(count, visible, depthBits);
        batch.accumulateBounds(bulk.boundsMin(), bulk.boundsMax());
        batch.adoptState(materialStates.snapshotFor(perSubmesh, materialCache, surfaceUniforms));
        instanceBatches.activate(batch);
    }

    private boolean batchable(PerSubmesh perSubmesh) {
        return instancingEnabled
                && !perSubmesh.material().blended()
                && perSubmesh.classResources().supportsInstancing();
    }

    private void flushInstanceBatches(FrameBuilder frame) {
        batchesThisFrame = instanceBatches.activeBatches().size();
        instancedBatchesThisFrame = 0;
        for (MeshInstanceBatch batch : instanceBatches.activeBatches()) {
            if (!batchBindingsAlive(batch)) {
                continue;
            }
            if (batch.pendingCount() == 1) {
                submitBatchAsSingleObject(frame, batch);
                continue;
            }
            instanceBatches.upload(batch);
            submitInstancedBatch(frame, batch);
            instancedBatchesThisFrame++;
        }
    }

    private boolean batchBindingsAlive(MeshInstanceBatch batch) {
        PerSubmesh perSubmesh = batch.representative();
        return perSubmesh != null
                && ownedBindings.contains(perSubmesh.litBindings())
                && ownedBindings.contains(perSubmesh.shadowBindings());
    }

    private void submitBatchAsSingleObject(FrameBuilder frame, MeshInstanceBatch batch) {
        PerSubmesh perSubmesh = batch.representative();
        long transformHash = ShadowSignatures.mixMatrix(ShadowSignatures.seed(), batch.firstModel());
        writeObjectUboIfChanged(perSubmesh.modelSlot(), batch.firstModel(), transformHash);
        submitSubmesh(frame, batch.submesh(), perSubmesh, batch.minDepthBits(), transformHash,
                batch.visibleCount() == 1, !batch.castsShadows(), false, false,
                batch.vertexColored(), false);
    }

    private void submitInstancedBatch(FrameBuilder frame, MeshInstanceBatch batch) {
        PerSubmesh perSubmesh = batch.representative();
        PipelineHandle pipeline = perSubmesh.classResources().pipeline();
        if (perSubmesh.material().blended()) {
            submitTransparentInstancedBatch(frame, batch, pipeline);
            return;
        }
        String surfacePath = MaterialPipelineCache.surfaceShaderPathOf(perSubmesh.material());
        if (batch.castsShadows()) {
            submitInstancedShadowCasters(batch, surfacePath);
        }
        if (gpuCullable(batch)) {
            submitIndirectInstancedBatch(frame, batch, pipeline);
            return;
        }
        if (batch.visibleCount() == 0) {
            return;
        }
        for (int tile = 0; tile < batch.tileCount(); tile++) {
            if (tileDrawCount(batch, tile) == 0 || !tileVisible(batch, tile)) {
                continue;
            }
            long tileDepth = tileDepthBits(batch, tile);
            submitDepthPrepass(frame, batch.submesh(), batch.shadowBindings(tile),
                    perSubmesh.material(), tileDepth, tileDrawCount(batch, tile), false,
                    batch.vertexColored(), perSubmesh.shadowMasked());
            frame.submit(RenderPasses.OPAQUE_3D, new DrawCommand(pipeline, batch.submesh().handle(),
                    batch.litBindings(tile), opaqueSortKey(pipeline, tileDepth),
                    tileDrawCount(batch, tile)));
        }
    }

    private boolean tileVisible(MeshInstanceBatch batch, int tile) {
        if (batch.tileCount() == 1) {
            return true;
        }
        batch.tileBounds(tile, scratchTileMin, scratchTileMax);
        if (withinRange(batch.visibilityRangeBegin(), batch.visibilityRangeEnd())) {
            return false;
        }
        return !culler.isCulled(scratchTileMin, scratchTileMax);
    }

    private boolean withinRange(float begin, float end) {
        if (begin <= 0.0f && end <= 0.0f) {
            return false;
        }
        float distance = distanceToBounds(scratchTileMin, scratchTileMax);
        return (end > 0.0f && distance > end) || (begin > 0.0f && distance < begin);
    }

    private long tileDepthBits(MeshInstanceBatch batch, int tile) {
        if (batch.tileCount() == 1) {
            return batch.minDepthBits();
        }
        batch.tileBounds(tile, scratchTileMin, scratchTileMax);
        return viewDepthBits((scratchTileMin.x + scratchTileMax.x) * 0.5f,
                (scratchTileMin.y + scratchTileMax.y) * 0.5f,
                (scratchTileMin.z + scratchTileMax.z) * 0.5f);
    }

    private int tileDrawCount(MeshInstanceBatch batch, int tile) {
        return batch.tileDrawCount(tile);
    }

    private void submitTransparentInstancedBatch(FrameBuilder frame, MeshInstanceBatch batch,
                                                 PipelineHandle pipeline) {
        if (batch.visibleCount() == 0) {
            return;
        }
        for (int tile = 0; tile < batch.tileCount(); tile++) {
            if (tileDrawCount(batch, tile) == 0 || !tileVisible(batch, tile)) {
                continue;
            }
            long backToFrontKey = 0xFFFFFFFFL - tileDepthBits(batch, tile);
            frame.submit(RenderPasses.TRANSPARENT_3D, new DrawCommand(pipeline, batch.submesh().handle(),
                    batch.litBindings(tile), backToFrontKey, tileDrawCount(batch, tile)));
        }
    }

    private void submitInstancedShadowCasters(MeshInstanceBatch batch, String surfacePath) {
        PerSubmesh perSubmesh = batch.representative();
        boolean frozen = shadowTimeFrozen(perSubmesh.material(), surfacePath);
        boolean timeAnimated = shadowTimeAnimated(perSubmesh.material(), surfacePath);
        if (timeAnimated) {
            shadowStatistics.recordAnimatedCaster();
        }
        long identity = ShadowSignatures.mix(
                ShadowSignatures.mix(INSTANCED_CASTER_DOMAIN, batch.submesh().handle().id()),
                batch.state().digest());
        long signature = instancedCasterSignature(batch, surfacePath, frozen);
        for (int tile = 0; tile < batch.tileCount(); tile++) {
            submitInstancedTileCasters(batch, perSubmesh, surfacePath, frozen, timeAnimated,
                    ShadowSignatures.mix(identity, tile), ShadowSignatures.mix(signature, tile), tile);
        }
    }

    private void submitInstancedTileCasters(MeshInstanceBatch batch, PerSubmesh perSubmesh, String surfacePath,
                                            boolean frozen, boolean timeAnimated, long identity, long signature,
                                            int tile) {
        int count = tileDrawCount(batch, tile);
        if (count == 0) {
            return;
        }
        batch.tileBounds(tile, scratchTileMin, scratchTileMax);
        if (shadowCascades.cascadesActive()) {
            shadowCascades.submitCaster(new DrawCommand(
                    shadowCascades.pipelineFor(surfacePath, perSubmesh.shadowMasked(), frozen, false,
                            batch.vertexColored()),
                    batch.submesh().handle(), batch.shadowBindings(tile), 0L, count),
                    identity, signature, timeAnimated, scratchTileMin, scratchTileMax);
        }
        if (spotShadows.activeCount() > 0) {
            spotShadows.submitCaster(new DrawCommand(
                    spotShadows.pipelineFor(surfacePath, frozen, false, batch.vertexColored()),
                    batch.submesh().handle(), batch.shadowBindings(tile), 0L, count),
                    identity, signature, timeAnimated, scratchTileMin, scratchTileMax);
        }
        if (pointShadows.activeCount() > 0) {
            pointShadows.submitCaster(new DrawCommand(
                    pointShadows.pipelineFor(surfacePath, frozen, false, batch.vertexColored()),
                    batch.submesh().handle(), batch.shadowBindings(tile), 0L, count),
                    identity, signature, timeAnimated, scratchTileMin, scratchTileMax);
        }
    }

    private static long instancedCasterSignature(MeshInstanceBatch batch, String surfacePath, boolean frozen) {
        PerSubmesh perSubmesh = batch.representative();
        long signature = ShadowSignatures.mix(batch.transformHash(), batch.submesh().handle().id());
        signature = ShadowSignatures.mix(signature, batch.tileCount());
        signature = ShadowSignatures.mix(signature, batch.instanceCount());
        signature = ShadowSignatures.mix(signature, surfacePath.hashCode());
        signature = ShadowSignatures.mix(signature, perSubmesh.shadowMasked() ? 1L : 0L);
        signature = ShadowSignatures.mix(signature, frozen ? 1L : 0L);
        signature = ShadowSignatures.mix(signature, alphaCutoffBits(perSubmesh.material()));
        return ShadowSignatures.mix(signature, SurfaceUniformBinder.valueRevisionOf(perSubmesh.material()));
    }

    private boolean shadowTimeFrozen(Material material, String surfacePath) {
        return material instanceof LitMaterial lit && !lit.animatedShadow()
                && surfaceTimeDependence.animatesShadow(surfacePath);
    }

    private boolean shadowTimeAnimated(Material material, String surfacePath) {
        return surfaceTimeDependence.animatesShadow(surfacePath) && !shadowTimeFrozen(material, surfacePath);
    }

    private void submitSubmesh(FrameBuilder frame, UploadedSubmesh submesh, PerSubmesh perSubmesh,
                               long depthBits, long transformHash, boolean visible,
                               boolean excludedFromShadows, boolean skinned, boolean deformedGeometry,
                               boolean colored, boolean viewModel) {
        PipelineHandle pipeline = perSubmesh.classResources().pipeline();
        if (viewModel) {
            if (visible) {
                frame.submit(RenderPasses.VIEW_MODEL_3D, new DrawCommand(pipeline, submesh.handle(),
                        perSubmesh.litBindings(), (pipeline.id() << 32) | depthBits, 1));
            }
            return;
        }
        if (perSubmesh.material().blended()) {
            if (!visible) {
                return;
            }
            long backToFrontKey = 0xFFFFFFFFL - depthBits;
            frame.submit(RenderPasses.TRANSPARENT_3D, new DrawCommand(pipeline, submesh.handle(), perSubmesh.litBindings(), backToFrontKey, 1));
            return;
        }
        if (!excludedFromShadows) {
            String surfacePath = MaterialPipelineCache.surfaceShaderPathOf(perSubmesh.material());
            submitShadowCasters(submesh, perSubmesh, surfacePath, transformHash, skinned,
                    skinned || deformedGeometry, colored);
        }
        if (!visible) {
            return;
        }
        submitDepthPrepass(frame, submesh, perSubmesh.shadowBindings(), perSubmesh.material(), depthBits, 1,
                skinned, colored, perSubmesh.shadowMasked());
        frame.submit(RenderPasses.OPAQUE_3D, new DrawCommand(pipeline, submesh.handle(),
                perSubmesh.litBindings(), opaqueSortKey(pipeline, depthBits), 1));
    }

    private void submitShadowCasters(UploadedSubmesh submesh, PerSubmesh perSubmesh, String surfacePath,
                                     long transformHash, boolean skinned, boolean deforming, boolean colored) {
        boolean frozen = shadowTimeFrozen(perSubmesh.material(), surfacePath);
        boolean animated = deforming || shadowTimeAnimated(perSubmesh.material(), surfacePath);
        if (animated) {
            shadowStatistics.recordAnimatedCaster();
        }
        long identity = ShadowSignatures.mix(
                ShadowSignatures.mix(SINGLE_CASTER_DOMAIN, submesh.handle().id()),
                perSubmesh.shadowBindings().id());
        long signature = casterSignature(submesh, perSubmesh, surfacePath, transformHash, frozen);
        if (shadowCascades.cascadesActive()) {
            PipelineHandle shadowPipeline =
                    shadowCascades.pipelineFor(surfacePath, perSubmesh.shadowMasked(), frozen, skinned, colored);
            shadowCascades.submitCaster(new DrawCommand(shadowPipeline, submesh.handle(),
                    perSubmesh.shadowBindings(), 0L, 1), identity, signature, animated,
                    scratchCasterMin, scratchCasterMax);
        }
        if (spotShadows.activeCount() > 0) {
            spotShadows.submitCaster(new DrawCommand(spotShadows.pipelineFor(surfacePath, frozen, skinned, colored),
                    submesh.handle(), perSubmesh.shadowBindings(), 0L, 1), identity, signature, animated,
                    scratchCasterMin, scratchCasterMax);
        }
        if (pointShadows.activeCount() > 0) {
            pointShadows.submitCaster(new DrawCommand(pointShadows.pipelineFor(surfacePath, frozen, skinned, colored),
                    submesh.handle(), perSubmesh.shadowBindings(), 0L, 1), identity, signature, animated,
                    scratchCasterMin, scratchCasterMax);
        }
    }

    private static long casterSignature(UploadedSubmesh submesh, PerSubmesh perSubmesh,
                                        String surfacePath, long transformHash, boolean frozen) {
        long signature = ShadowSignatures.mix(transformHash, submesh.handle().id());
        signature = ShadowSignatures.mix(signature, perSubmesh.shadowBindings().id());
        signature = ShadowSignatures.mix(signature, surfacePath.hashCode());
        signature = ShadowSignatures.mix(signature, perSubmesh.shadowMasked() ? 1L : 0L);
        signature = ShadowSignatures.mix(signature, frozen ? 1L : 0L);
        signature = ShadowSignatures.mix(signature, alphaCutoffBits(perSubmesh.material()));
        return ShadowSignatures.mix(signature, SurfaceUniformBinder.valueRevisionOf(perSubmesh.material()));
    }

    private static long alphaCutoffBits(Material material) {
        return material instanceof LitMaterial lit ? Float.floatToRawIntBits(lit.alphaCutoff) : 0L;
    }

    private UploadedMesh selectLevelOfDetail(MeshRenderer renderer, UploadedMesh baseMesh, Matrix4f modelMatrix) {
        if (renderer.levelOfDetailCount() == 0) {
            return baseMesh;
        }
        float dx = modelMatrix.m30() - scratchCameraPosition.x;
        float dy = modelMatrix.m31() - scratchCameraPosition.y;
        float dz = modelMatrix.m32() - scratchCameraPosition.z;
        UploadedMesh selected = renderer.meshForDistance((float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        return selected == null ? baseMesh : selected;
    }

    private UploadedMesh selectLevelOfDetail(MultiMeshRenderer renderer, UploadedMesh baseMesh,
                                             Vector3f boundsMin, Vector3f boundsMax) {
        if (renderer.levelOfDetailCount() == 0) {
            return baseMesh;
        }
        float dx = (boundsMin.x + boundsMax.x) * 0.5f - scratchCameraPosition.x;
        float dy = (boundsMin.y + boundsMax.y) * 0.5f - scratchCameraPosition.y;
        float dz = (boundsMin.z + boundsMax.z) * 0.5f - scratchCameraPosition.z;
        UploadedMesh selected = renderer.meshForDistance((float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        if (selected == baseMesh || selected.submeshes().size() == baseMesh.submeshes().size()) {
            return selected;
        }
        logSubmeshCountMismatchOnce(renderer, selected, baseMesh);
        return baseMesh;
    }

    private void logSubmeshCountMismatchOnce(MultiMeshRenderer renderer, UploadedMesh selected,
                                             UploadedMesh baseMesh) {
        if (!mismatchedLevelMeshes.add(selected)) {
            return;
        }
        logger.warn("Multi mesh renderer level of detail " + renderer.activeLevelOfDetail() + " has "
                + selected.submeshes().size() + " submeshes but the base mesh has "
                + baseMesh.submeshes().size() + "; drawing the base mesh instead.");
    }

    private boolean outOfVisibilityRange(MeshRenderSource renderer, Vector3f worldMin, Vector3f worldMax) {
        float begin = renderer.visibilityRangeBegin();
        float end = renderer.visibilityRangeEnd();
        if (begin <= 0.0f && end <= 0.0f) {
            return false;
        }
        float distance = distanceToBounds(worldMin, worldMax);
        return (end > 0.0f && distance > end) || (begin > 0.0f && distance < begin);
    }

    private float distanceToBounds(Vector3f worldMin, Vector3f worldMax) {
        float dx = Math.max(0.0f, Math.max(worldMin.x - scratchCameraPosition.x, scratchCameraPosition.x - worldMax.x));
        float dy = Math.max(0.0f, Math.max(worldMin.y - scratchCameraPosition.y, scratchCameraPosition.y - worldMax.y));
        float dz = Math.max(0.0f, Math.max(worldMin.z - scratchCameraPosition.z, scratchCameraPosition.z - worldMax.z));
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private long opaqueSortKey(PipelineHandle pipeline, long depthBits) {
        if (frontToBackOpaque) {
            return (depthBits << 32) | (pipeline.id() & 0xFFFFFFFFL);
        }
        return (pipeline.id() << 32) | depthBits;
    }

    private long viewDepthBits(Matrix4f modelMatrix) {
        return viewDepthBits(modelMatrix.m30(), modelMatrix.m31(), modelMatrix.m32());
    }

    private long viewDepthBits(float x, float y, float z) {
        float dx = x - scratchCameraPosition.x;
        float dy = y - scratchCameraPosition.y;
        float dz = z - scratchCameraPosition.z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        return Float.floatToIntBits(distanceSquared) & 0xFFFFFFFFL;
    }

    private void writeObjectUboIfChanged(ObjectUniformSlot slot, Matrix4f model, long transformHash) {
        if (!slot.needsWrite(transformHash)) {
            return;
        }
        packObjectUbo(model);
        objectUniforms().write(slot, scratchObjectUbo, transformHash);
    }

    private ObjectUniformArena objectUniforms() {
        if (objectUniforms == null) {
            objectUniforms = new ObjectUniformArena(backend, ringObjectUniforms);
        }
        return objectUniforms;
    }

    public void setRingObjectUniforms(boolean value) {
        if (value == ringObjectUniforms) {
            return;
        }
        ringObjectUniforms = value;
        invalidateObjectResources();
        if (objectUniforms != null) {
            objectUniforms.destroy();
            objectUniforms = null;
        }
    }

    public int objectUniformBlockCount() {
        return objectUniforms == null ? 0 : objectUniforms.blockCount();
    }

    public boolean ringObjectUniforms() {
        return ringObjectUniforms;
    }

    private void writeFallbackFrameViewProjection() {
        if (backend == null) {
            return;
        }
        scratchFrameViewProjection.clear();
        lastCameraViewProjection.get(0, scratchFrameViewProjection);
        scratchFrameViewProjection.position(0);
        scratchFrameViewProjection.limit(FRAME_VIEW_PROJECTION_BYTES);
        backend.writeBuffer(frameUboWriter.handle(), scratchFrameViewProjection, 0L);
    }

    private void packObjectUbo(Matrix4f model) {
        scratchObjectUbo.clear();
        model.get(0, scratchObjectUbo);
        model.normal(scratchNormalMatrix);
        scratchNormalMatrix.get(64, scratchObjectUbo);
        scratchObjectUbo.position(0);
        scratchObjectUbo.limit(MeshShaderBindings.OBJECT_UBO_SIZE);
    }

    private void computeCachedWorldBounds(MeshRenderSource renderer, Transform3D transform,
                                          Aabb localBounds, Matrix4f modelMatrix, float alpha) {
        CachedWorldBounds cached = boundsCache.get(renderer);
        if (cached == null) {
            cached = new CachedWorldBounds();
            boundsCache.put(renderer, cached);
        }
        if (cached.matches(transform, localBounds, alpha)) {
            boundsCacheHits++;
            scratchCasterMin.set(cached.min);
            scratchCasterMax.set(cached.max);
            return;
        }
        boundsCacheMisses++;
        culler.computeWorldBounds(localBounds, modelMatrix, scratchCasterMin, scratchCasterMax);
        cached.store(transform, localBounds, alpha, scratchCasterMin, scratchCasterMax);
    }

    public int boundsCacheHits() {
        return boundsCacheHits;
    }

    public int boundsCacheMisses() {
        return boundsCacheMisses;
    }

    private static final class CachedWorldBounds {
        private final Vector3f min = new Vector3f();
        private final Vector3f max = new Vector3f();
        private Aabb localBounds;
        private long worldVersion = -1L;
        private boolean stored;

        boolean matches(Transform3D transform, Aabb candidateBounds, float alpha) {
            return stored
                    && localBounds == candidateBounds
                    && worldVersion == transform.worldVersion()
                    && transform.worldMatrixStable(alpha);
        }

        void store(Transform3D transform, Aabb candidateBounds, float alpha,
                   Vector3f worldMin, Vector3f worldMax) {
            min.set(worldMin);
            max.set(worldMax);
            localBounds = candidateBounds;
            worldVersion = transform.worldVersion();
            stored = transform.worldMatrixStable(alpha);
        }
    }

    private boolean addToMultiDrawGroup(UploadedMesh mesh, UploadedSubmesh submesh, PerSubmesh perSubmesh,
                                        Matrix4f modelMatrix, long depthBits) {
        if (!multiDrawEnabled || mesh.arenaPlacement().isEmpty() || perSubmesh.lightmapUvs().isPresent()) {
            return false;
        }
        MeshArena arena = mesh.arenaPlacement().get().arena();
        MultiDrawKey key = new MultiDrawKey(perSubmesh.material(), arena);
        MultiDrawGroup group = multiDrawGroups.computeIfAbsent(key,
                ignored -> new MultiDrawGroup(backend, arena));
        multiDrawPending.add(group);
        group.ensureCapacity(group.pendingCount() + 1);
        if (!group.hasBindings()) {
            adoptMultiDrawBindings(group, perSubmesh, mesh.vertexColored());
        }
        group.append(mesh.arenaPlacement().get().allocation(), modelMatrix, depthBits);
        return true;
    }

    private void adoptMultiDrawBindings(MultiDrawGroup group, PerSubmesh perSubmesh, boolean colored) {
        Material material = perSubmesh.material();
        MaterialClassResources resources =
                materialCache.classResourcesFor(material, false, colored, false, true);
        BufferHandle materialUbo = materialCache.ensureMaterialUbo(material, resources);
        BindingSetHandle bindings = backend.createBindingSet(buildLitBindingSetDescriptor(
                material, resources,
                multiDrawTransformBindings(group.transforms(), group.transformByteSize()),
                materialUbo, resources.litBindingLayout(), Optional.empty(), Optional.empty()));
        ownedBindings.add(bindings);
        group.adoptBindings(resources.pipeline(), bindings);
    }

    private void beginMultiDrawGroups() {
        for (MultiDrawGroup group : multiDrawGroups.values()) {
            group.begin();
        }
        multiDrawPending.clear();
    }

    private void flushMultiDrawGroups(FrameBuilder frame) {
        multiDrawsThisFrame = 0;
        multiDrawGeometriesThisFrame = 0;
        for (MultiDrawGroup group : multiDrawPending) {
            multiDrawGeometriesThisFrame += group.pendingCount();
            Optional<DrawCommand> command = group.flush();
            if (command.isPresent()) {
                frame.submit(RenderPasses.OPAQUE_3D, command.get());
                multiDrawsThisFrame++;
            }
        }
    }

    public int multiDrawCount() {
        return multiDrawsThisFrame;
    }

    public int multiDrawGeometryCount() {
        return multiDrawGeometriesThisFrame;
    }

    private record MultiDrawKey(Material material, MeshArena arena) {
    }

    private void purgeOrphanBounds() {
        if (boundsCache.size() == objectResources.size()) {
            return;
        }
        boundsCache.keySet().retainAll(objectResources.keySet());
    }

    private RenderableMesh cachedRenderableOf(PreparedEntry prepared, UploadedMesh mesh) {
        if (prepared == null || prepared.renderable == null
                || prepared.renderableGeneration != renderableGeneration
                || prepared.renderable.mesh() != mesh) {
            return null;
        }
        return prepared.renderable;
    }

    private void markSeen(RenderableMesh renderable) {
        if (renderable.lastSeenFrame != renderFrameCounter) {
            renderable.lastSeenFrame = renderFrameCounter;
            renderersSeenCount++;
        }
    }

    private void purgeOrphanRenderers() {
        if (objectResources.size() == renderersSeenCount || sceneVersionAtPurge == lastSceneVersion) {
            return;
        }
        sceneVersionAtPurge = lastSceneVersion;
        Iterator<Map.Entry<MeshRenderSource, RenderableMesh>> iterator = objectResources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MeshRenderSource, RenderableMesh> entry = iterator.next();
            if (entry.getValue().lastSeenFrame == renderFrameCounter || stillAttached(entry.getKey())) {
                continue;
            }
            destroyRenderable(entry.getValue());
            renderableGeneration++;
            if (entry.getKey() instanceof MultiMeshRenderer instanced) {
                instanceBatches.releaseBulk(instanced);
            }
            iterator.remove();
        }
    }

    private static boolean stillAttached(MeshRenderSource renderer) {
        return renderer instanceof Component component && component.ownerOrNull() != null;
    }

    public int warmUpPipelines(Scene scene) {
        int warmed = 0;
        for (MeshRenderer renderer : scene.componentsOf(MeshRenderer.class)) {
            UploadedMesh mesh = renderer.meshOrNull();
            if (mesh == null) {
                continue;
            }
            resolvePerSubmeshes(renderer, mesh);
            warmed++;
        }
        for (MultiMeshRenderer renderer : scene.componentsOf(MultiMeshRenderer.class)) {
            UploadedMesh mesh = renderer.meshOrNull();
            if (mesh == null || renderer.materialOrNull() == null) {
                continue;
            }
            resolvePerSubmeshes(renderer, mesh);
            warmed++;
        }
        return warmed;
    }

    private RenderableMesh resolvePerSubmeshes(MeshRenderSource renderer, UploadedMesh mesh) {
        RenderableMesh cached = objectResources.get(renderer);
        if (cached != null && cached.mesh() == mesh) {
            return cached;
        }
        if (cached != null) {
            destroyRenderable(cached);
        }
        Optional<JointPalette> palette = createJointPaletteIfSkinned(mesh);
        Optional<DeformedMesh> deformed = createDeformedMeshIfSupported(mesh, palette);
        Optional<JointPalette> pipelinePalette = deformed.isPresent() ? Optional.empty() : palette;
        RenderableMesh rebuilt = new RenderableMesh(mesh,
                createPerSubmeshes(renderer, mesh, pipelinePalette, mesh.skinned() && deformed.isEmpty()),
                palette, deformed);
        objectResources.put(renderer, rebuilt);
        return rebuilt;
    }

    private void destroyRenderable(RenderableMesh renderable) {
        destroyPerSubmeshes(renderable.submeshes());
        renderable.jointPalette().ifPresent(this::destroyJointPalette);
        renderable.deformedMesh().ifPresent(deformed -> deformed.destroy(backend));
    }

    private Optional<DeformedMesh> createDeformedMeshIfSupported(UploadedMesh mesh,
                                                                 Optional<JointPalette> palette) {
        if (!skinOnceEnabled || palette.isEmpty() || !SkinnedDeformer.supports(mesh)) {
            return Optional.empty();
        }
        JointPalette resolved = palette.orElseThrow();
        return Optional.of(ensureSkinnedDeformer().create(mesh, resolved.buffer(), resolved.byteSize()));
    }

    private SkinnedDeformer ensureSkinnedDeformer() {
        if (skinnedDeformer == null) {
            skinnedDeformer = new SkinnedDeformer(backend, shaderLoader);
        }
        return skinnedDeformer;
    }

    private Optional<JointPalette> createJointPaletteIfSkinned(UploadedMesh mesh) {
        if (!mesh.skinned()) {
            return Optional.empty();
        }
        int jointCount = mesh.skeleton().orElseThrow(() ->
                new EpysiaException("Skinned mesh missing skeleton at palette creation")).jointCount();
        long byteSize = (long) jointCount * MeshShaderBindings.JOINT_PALETTE_BYTES_PER_JOINT;
        ByteBuffer identity = BufferUtils.createByteBuffer((int) byteSize);
        writeIdentityPalette(identity, jointCount);
        BufferHandle buffer = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, identity));
        ownedBuffers.add(buffer);
        JointPalette palette = new JointPalette(buffer, byteSize, jointCount);
        palette.phase = animationPhaseCounter++;
        return Optional.of(palette);
    }

    private static boolean castsShadows(MeshRenderer renderer) {
        return renderer.castsShadows() && !renderer.viewModel();
    }

    private void samplePendingPoses() {
        if (pendingPoseCount == 0) {
            return;
        }
        if (!parallelAnimation || pendingPoseCount < MINIMUM_POSES_PER_WORKER) {
            samplePoseRange(0, pendingPoseCount, 0);
            return;
        }
        frameWorkers().split(pendingPoseCount, MINIMUM_POSES_PER_WORKER, this::samplePoseRange);
    }

    private void samplePoseRange(int from, int toExclusive, int worker) {
        PoseSamplingContext context = poseContexts[worker];
        for (int index = from; index < toExclusive; index++) {
            PendingPose pending = pendingPoses[index];
            samplePose(pending, context);
            packPose(pending, context);
        }
    }

    private void publishPendingPoses() {
        for (int index = 0; index < pendingPoseCount; index++) {
            PendingPose pending = pendingPoses[index];
            JointPalette palette = pending.palette;
            backend.writeBuffer(palette.buffer, palette.packBuffer, 0L);
            pending.renderable.deformedMesh().ifPresent(DeformedMesh::markStale);
            pending.clear();
        }
        posesSampled += pendingPoseCount;
    }

    private FrameWorkers frameWorkers() {
        if (frameWorkers == null) {
            frameWorkers = new FrameWorkers();
            poseContexts = PoseSamplingContext.create(frameWorkers.workerCount());
        }
        return frameWorkers;
    }

    private PendingPose reservePendingPose() {
        if (pendingPoseCount == pendingPoses.length) {
            pendingPoses = Arrays.copyOf(pendingPoses, Math.max(8, pendingPoses.length * 2));
        }
        PendingPose reserved = pendingPoses[pendingPoseCount];
        if (reserved == null) {
            reserved = new PendingPose();
            pendingPoses[pendingPoseCount] = reserved;
        }
        pendingPoseCount++;
        return reserved;
    }

    private void updateAnimatedPalette(GameObject gameObject, UploadedMesh mesh,
                                       RenderableMesh renderable, float alpha, boolean castsShadows) {
        JointPalette palette = renderable.jointPaletteOrNull();
        if (palette == null || palette.lastAnimationGeneration == animationGeneration) {
            return;
        }
        palette.lastAnimationGeneration = animationGeneration;
        Animator animator = gameObject.getComponentOrNull(Animator.class);
        if (animator == null || !animator.isPlaying()) {
            return;
        }
        Skeleton skeleton = mesh.skeleton().orElseThrow(() ->
                new EpysiaException("Skinned mesh missing skeleton during animation."));
        if (!animatorMatchesSkeleton(gameObject, animator, skeleton, palette)) {
            return;
        }
        if (!shouldSamplePose(gameObject, mesh, palette, alpha, castsShadows)) {
            return;
        }
        reservePendingPose().adopt(gameObject, animator, mesh, renderable, palette,
                bindPoseCache.of(skeleton), skeleton);
    }

    private void deformIfStale(RenderableMesh renderable) {
        if (renderable.deformedMesh().isEmpty()) {
            return;
        }
        DeformedMesh deformed = renderable.deformedMesh().orElseThrow();
        if (deformed.consumeStale()) {
            skinnedDeformer.dispatch(deformed);
        }
    }

    private boolean animatorMatchesSkeleton(GameObject gameObject, Animator animator, Skeleton skeleton,
                                            JointPalette palette) {
        if (animator.blendSpaceActive()) {
            return true;
        }
        Clip clip = animator.resolvedClip().orElse(null);
        if (clip == null) {
            return false;
        }
        if (clip.skeletonChecksum() != skeleton.nameChecksum()) {
            logChecksumMismatchOnce(gameObject, clip, skeleton, palette);
            return false;
        }
        return true;
    }

    private void samplePose(PendingPose pending, PoseSamplingContext context) {
        JointPalette palette = pending.palette;
        if (!sampleBlendSpace(pending.animator, pending.bindPose, palette, context)) {
            samplePrimaryClip(pending.animator, pending.bindPose, palette, context);
        }
        applyCrossFade(pending.animator, pending.bindPose, palette, context);
        applyLayers(pending.gameObject, pending.animator, pending.bindPose, palette, context);
        palette.pose.computeSkinningMatrices(pending.skeleton, palette.skinningMatrices);
    }

    private void packPose(PendingPose pending, PoseSamplingContext context) {
        JointPalette palette = pending.palette;
        if (pending.mesh.localBounds() != null) {
            palette.animatedBounds.refresh(palette.skinningMatrices, pending.mesh.localBounds(),
                    context.scratchCorner());
        }
        SkinningPalette.pack(palette.skinningMatrices, palette.packBuffer, palette.rowScratch);
    }

    private void samplePrimaryClip(Animator animator, BindPose bindPose, JointPalette palette,
                                   PoseSamplingContext context) {
        Clip clip = animator.resolvedClip().orElse(null);
        if (clip == null) {
            bindPose.copyInto(palette.pose);
            return;
        }
        context.clipSampler().sample(clip, bindPose, animator.currentTimeSeconds(), palette.pose);
    }

    private boolean sampleBlendSpace(Animator animator, BindPose bindPose, JointPalette palette,
                                     PoseSamplingContext context) {
        if (!animator.blendSpaceActive()) {
            return false;
        }
        return context.blendSpaceSampler().sample(animator.blendSamples(), animator.currentBlendWeights(),
                bindPose, animator.blendPhase(), palette.pose, palette.layerPose);
    }

    private boolean shouldSamplePose(GameObject gameObject, UploadedMesh mesh, JointPalette palette,
                                     float alpha, boolean castsShadows) {
        Transform3D transform = transformOf(gameObject);
        if (transform == null || !poseSchedule.cullingEnabled()) {
            return true;
        }
        Aabb localBounds = palette.animatedBounds.computed()
                ? palette.animatedBounds.bounds()
                : mesh.localBounds();
        if (localBounds == null) {
            return true;
        }
        culler.computeWorldBounds(localBounds, transform.worldMatrix(alpha),
                scratchAnimationMinimum, scratchAnimationMaximum);
        boolean relevant = sceneRelevance.overlaps(castsShadows,
                scratchAnimationMinimum.x, scratchAnimationMinimum.y, scratchAnimationMinimum.z,
                scratchAnimationMaximum.x, scratchAnimationMaximum.y, scratchAnimationMaximum.z);
        return poseSchedule.samplesThisFrame(relevant, distanceToCameraSquared(), animationGeneration, palette.phase);
    }

    private float distanceToCameraSquared() {
        float x = Math.max(scratchAnimationMinimum.x - scratchCameraPosition.x,
                Math.max(0.0f, scratchCameraPosition.x - scratchAnimationMaximum.x));
        float y = Math.max(scratchAnimationMinimum.y - scratchCameraPosition.y,
                Math.max(0.0f, scratchCameraPosition.y - scratchAnimationMaximum.y));
        float z = Math.max(scratchAnimationMinimum.z - scratchCameraPosition.z,
                Math.max(0.0f, scratchCameraPosition.z - scratchAnimationMaximum.z));
        return x * x + y * y + z * z;
    }

    private void applyCrossFade(Animator animator, BindPose bindPose, JointPalette palette,
                                PoseSamplingContext context) {
        if (!animator.isFading() || animator.previousClip().isEmpty()) {
            return;
        }
        Clip previous = animator.previousClip().get();
        if (previous.skeletonChecksum() != bindPose.skeleton().nameChecksum()) {
            return;
        }
        context.clipSampler().sample(previous, bindPose, animator.previousTimeSeconds(), palette.previousPose);
        palette.pose.blendFrom(palette.previousPose, animator.fadeAlpha());
    }

    private void applyLayers(GameObject gameObject, Animator animator, BindPose bindPose,
                             JointPalette palette, PoseSamplingContext context) {
        for (AnimationLayer layer : animator.layers()) {
            applyLayer(gameObject, layer, bindPose, palette, context);
        }
    }

    private void applyLayer(GameObject gameObject, AnimationLayer layer, BindPose bindPose,
                            JointPalette palette, PoseSamplingContext context) {
        if (!layer.contributes()) {
            return;
        }
        Skeleton skeleton = bindPose.skeleton();
        Clip clip = layer.resolvedClip().orElseThrow();
        if (clip.skeletonChecksum() != skeleton.nameChecksum()) {
            logLayerMismatchOnce(gameObject, layer, palette, "clip checksum " + clip.skeletonChecksum()
                    + " does not match skeleton " + skeleton.nameChecksum());
            return;
        }
        if (layer.maskRootIsMissing(skeleton)) {
            logLayerMismatchOnce(gameObject, layer, palette,
                    "mask root joint '" + layer.maskRootJoint() + "' is absent from the skeleton");
            return;
        }
        context.clipSampler().sample(clip, bindPose, layer.currentTimeSeconds(), palette.layerPose);
        context.poseLayerBlend().apply(palette.pose, palette.layerPose, skeleton,
                layer.blendMode(), layer.weight(), layer.maskFor(skeleton));
    }

    private void logLayerMismatchOnce(GameObject gameObject, AnimationLayer layer,
                                      JointPalette palette, String reason) {
        if (!palette.warnedLayers.add(layer)) {
            return;
        }
        logger.warn("Animator layer '" + layer.clipPath() + "' on '" + gameObject.name()
                + "' skipped: " + reason + ".");
    }

    private Aabb cullBounds(UploadedMesh mesh, RenderableMesh renderable) {
        if (!mesh.skinned()) {
            return mesh.localBounds();
        }
        JointPalette palette = renderable.jointPaletteOrNull();
        if (palette == null || !palette.animatedBounds.computed()) {
            return mesh.localBounds();
        }
        return palette.animatedBounds.bounds();
    }

    private void logChecksumMismatchOnce(GameObject gameObject, Clip clip, Skeleton skeleton, JointPalette palette) {
        if (palette.checksumMismatchLogged) {
            return;
        }
        palette.checksumMismatchLogged = true;
        logger.warn("Animator on '" + gameObject.name() + "' clip checksum " + clip.skeletonChecksum()
                + " does not match skeleton " + skeleton.nameChecksum() + "; keeping bind pose.");
    }

    private static void writeIdentityPalette(ByteBuffer buffer, int jointCount) {
        buffer.clear();
        for (int joint = 0; joint < jointCount; joint++) {
            buffer.putFloat(1.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
            buffer.putFloat(0.0f).putFloat(1.0f).putFloat(0.0f).putFloat(0.0f);
            buffer.putFloat(0.0f).putFloat(0.0f).putFloat(1.0f).putFloat(0.0f);
        }
        buffer.flip();
    }

    private void destroyJointPalette(JointPalette palette) {
        backend.destroy(palette.buffer());
        ownedBuffers.remove(palette.buffer());
    }

    private void destroyPerSubmeshes(List<PerSubmesh> perSubmeshes) {
        for (PerSubmesh perSubmesh : perSubmeshes) {
            destroyPerSubmesh(perSubmesh);
        }
    }

    private void destroyPerSubmesh(PerSubmesh perSubmesh) {
        instanceBatches.forget(perSubmesh);
        backend.destroy(perSubmesh.litBindings());
        backend.destroy(perSubmesh.shadowBindings());
        objectUniforms().release(perSubmesh.modelSlot());
        ownedBindings.remove(perSubmesh.litBindings());
        ownedBindings.remove(perSubmesh.shadowBindings());

    }

    private Material resolveMaterial(MeshRenderSource renderer, int slot) {
        Material forSlot = renderer.materialForSlot(slot).orElse(null);
        if (forSlot != null) {
            return forSlot;
        }
        Material primary = renderer.materialForSlot(0).orElse(null);
        return primary != null ? primary : fallbackMaterial();
    }

    private Material fallbackMaterial() {
        if (fallback == null) {
            fallback = new LitMaterial();
        }
        return fallback;
    }

    private List<PerSubmesh> createPerSubmeshes(MeshRenderSource renderer, UploadedMesh mesh,
                                                Optional<JointPalette> jointPalette, boolean skinnedPipelines) {
        List<PerSubmesh> result = new ArrayList<>(mesh.submeshes().size());
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            result.add(createPerSubmesh(renderer, submesh, skinnedPipelines, mesh.vertexColored(),
                    jointPalette, mesh.lightmapUvs()));
        }
        return result;
    }

    private PerSubmesh createPerSubmesh(MeshRenderSource renderer, UploadedSubmesh submesh, boolean skinned,
                                        boolean colored, Optional<JointPalette> jointPalette,
                                        Optional<StorageBufferBinding> lightmapUvs) {
        Material material = resolveMaterial(renderer, submesh.materialSlot());
        MaterialClassResources classResources =
                materialCache.classResourcesFor(material, skinned, colored, lightmapUvs.isPresent());
        BufferHandle materialUbo = materialCache.ensureMaterialUbo(material, classResources);
        ObjectUniformSlot modelSlot = objectUniforms().allocate();
        boolean shadowMasked = shadowMasked(material, materialUbo);
        BindingSetHandle shadowBindings =
                createShadowBindings(material, classResources, modelSlot, materialUbo, shadowMasked, jointPalette);
        BindingSetHandle litBindings = backend.createBindingSet(
                buildLitBindingSetDescriptor(material, classResources, modelSlot, materialUbo, jointPalette,
                        lightmapUvs));
        ownedBindings.add(shadowBindings);
        ownedBindings.add(litBindings);
        return new PerSubmesh(modelSlot, shadowBindings, litBindings, classResources, material,
                captureTextures(material, classResources), shadowMasked,
                SurfaceUniformBinder.structureRevisionOf(material), lightmapUvs);
    }

    private static boolean shadowMasked(Material material, BufferHandle materialUbo) {
        return materialUbo != null && material instanceof LitMaterial lit && lit.alphaCutoff > 0.0f;
    }

    private BindingSetHandle createShadowBindings(Material material, MaterialClassResources classResources,
                                                  ObjectUniformSlot modelSlot, BufferHandle materialUbo,
                                                  boolean masked, Optional<JointPalette> jointPalette) {
        BindingSetLayout layout = jointPalette.isPresent()
                ? shadowCascades.skinnedBindingLayout() : shadowCascades.bindingLayout();
        BindingSetLayout maskedLayout = jointPalette.isPresent()
                ? shadowCascades.maskedSkinnedBindingLayout() : shadowCascades.maskedBindingLayout();
        return createShadowBindings(material, classResources, objectTransformBindings(modelSlot), materialUbo, masked,
                layout, maskedLayout, jointPalette);
    }

    private BindingSetHandle createShadowBindings(Material material, MaterialClassResources classResources,
                                                  List<Binding> transformBindings, BufferHandle materialUbo, boolean masked,
                                                  BindingSetLayout layout, BindingSetLayout maskedLayout,
                                                  Optional<JointPalette> jointPalette) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                UniformBufferBinding.whole(frameUboWriter.handle(), MeshShaderBindings.FRAME_UBO_SIZE)));
        bindings.addAll(transformBindings);
        bindings.add(new Binding(MeshShaderBindings.CASCADE_UBO_BINDING,
                UniformBufferBinding.whole(shadowCascades.cascadeUbo(), MeshShaderBindings.CASCADE_UBO_SIZE)));
        jointPalette.ifPresent(palette -> bindings.add(new Binding(MeshShaderBindings.JOINT_PALETTE_SSBO_BINDING,
                StorageBufferBinding.whole(palette.buffer(), palette.byteSize()))));
        if (!masked) {
            surfaceUniforms.appendBindings(bindings, material, classResources.surfaceUniforms());
            return backend.createBindingSet(new BindingSetDescriptor(layout, bindings));
        }
        bindings.add(new Binding(MeshShaderBindings.SHADOW_MASK_MATERIAL_UBO_BINDING,
                UniformBufferBinding.whole(materialUbo, classResources.metadata().uniformBufferSize())));
        bindings.add(new Binding(MeshShaderBindings.SHADOW_MASK_ALBEDO_BINDING,
                new SampledTextureBinding(shadowAlbedoTexture(material, classResources))));
        surfaceUniforms.appendBindings(bindings, material, classResources.surfaceUniforms());
        return backend.createBindingSet(new BindingSetDescriptor(maskedLayout, bindings));
    }

    private BindingSetHandle createInstanceBindingSet(PerSubmesh perSubmesh, BufferHandle instanceBuffer,
                                                      long byteOffset, long byteSize, boolean shadow) {
        List<Binding> transformBindings =
                instanceTransformBindings(perSubmesh.modelSlot(), instanceBuffer, byteOffset, byteSize);
        Material material = perSubmesh.material();
        MaterialClassResources classResources = perSubmesh.classResources();
        BufferHandle materialUbo = materialCache.materialUboFor(material);
        if (!shadow) {
            return backend.createBindingSet(buildLitBindingSetDescriptor(material, classResources, transformBindings,
                    materialUbo, classResources.litBindingLayout(), Optional.empty(), perSubmesh.lightmapUvs()));
        }
        return createShadowBindings(material, classResources, transformBindings, materialUbo,
                perSubmesh.shadowMasked(), shadowCascades.bindingLayout(),
                shadowCascades.maskedBindingLayout(), Optional.empty());
    }

    private TextureHandle shadowAlbedoTexture(Material material, MaterialClassResources classResources) {
        for (TextureFieldDescriptor field : classResources.metadata().textureFields()) {
            if (field.reflectField().getName().equals(SHADOW_MASK_ALBEDO_FIELD)) {
                TextureHandle texture = classResources.metadata().readTexture(material, field);
                return texture != null ? texture : materialCache.defaultFor(field);
            }
        }
        throw new EpysiaException("Alpha-masked material has no albedo texture field: " + material.getClass().getName());
    }

    private TextureHandle[] captureTextures(Material material, MaterialClassResources classResources) {
        List<TextureFieldDescriptor> textureFields = classResources.metadata().textureFields();
        TextureHandle[] snapshot = new TextureHandle[textureFields.size()];
        for (int i = 0; i < textureFields.size(); i++) {
            TextureHandle current = classResources.metadata().readTexture(material, textureFields.get(i));
            snapshot[i] = current != null ? current : materialCache.defaultFor(textureFields.get(i));
        }
        return snapshot;
    }

    private void refreshStalePerSubmeshes(MeshRenderSource renderer, UploadedMesh mesh,
                                          RenderableMesh renderable) {
        List<PerSubmesh> perSubmeshes = renderable.submeshes();
        Optional<JointPalette> jointPalette = renderable.pipelinePalette();
        boolean skinned = renderable.skinnedPipelines();
        boolean colored = mesh.vertexColored();
        Optional<StorageBufferBinding> lightmapUvs = mesh.lightmapUvs();
        for (int i = 0; i < perSubmeshes.size(); i++) {
            PerSubmesh existing = perSubmeshes.get(i);
            UploadedSubmesh submesh = mesh.submeshes().get(i);
            Material current = resolveMaterial(renderer, submesh.materialSlot());
            if (materialOrPipelineChanged(current, existing, skinned, colored, lightmapUvs.isPresent())) {
                destroyPerSubmesh(existing);
                perSubmeshes.set(i, createPerSubmesh(renderer, submesh, skinned, colored, jointPalette, lightmapUvs));
                continue;
            }
            if (materialChangedThisFrame(existing)) {
                refreshTextureBindingsAt(perSubmeshes, i, jointPalette);
            }
        }
    }

    private Transform3D transformOf(GameObject gameObject) {
        return transformLookupCached
                ? gameObject.transform3DOrNull()
                : gameObject.getComponentOrNull(Transform3D.class);
    }

    public void setTransformLookupCached(boolean value) {
        transformLookupCached = value;
    }

    private boolean materialOrPipelineChanged(Material current, PerSubmesh existing, boolean skinned,
                                              boolean colored, boolean lightmapped) {
        return current != existing.material()
                || frameClassResourcesFor(current, skinned, colored, lightmapped) != existing.classResources();
    }

    private MaterialClassResources frameClassResourcesFor(Material material, boolean skinned, boolean colored,
                                                          boolean lightmapped) {
        if (!pipelineMemoEnabled) {
            return materialCache.classResourcesFor(material, skinned, colored, lightmapped);
        }
        if (material == memoPipelineMaterial && skinned == memoPipelineSkinned
                && colored == memoPipelineColored && lightmapped == memoPipelineLightmapped
                && memoPipelineClassResources != null) {
            return memoPipelineClassResources;
        }
        MaterialClassResources resolved = materialCache.classResourcesFor(material, skinned, colored, lightmapped);
        memoPipelineLightmapped = lightmapped;
        memoPipelineMaterial = material;
        memoPipelineSkinned = skinned;
        memoPipelineColored = colored;
        memoPipelineClassResources = resolved;
        return resolved;
    }

    public void setPipelineMemoEnabled(boolean value) {
        pipelineMemoEnabled = value;
        memoPipelineMaterial = null;
        memoPipelineClassResources = null;
    }

    private static final boolean MATERIAL_GATING =
            Boolean.parseBoolean(System.getProperty("epysia.render.materialGating", "false"));

    private boolean materialChangedThisFrame(PerSubmesh perSubmesh) {
        if (!MATERIAL_GATING) {
            return true;
        }
        Material material = perSubmesh.material();
        Boolean known = materialChangedCache.get(material);
        if (known != null) {
            return known;
        }
        long signature = materialSignatureOf(perSubmesh);
        Long previous = materialSignatures.put(material, signature);
        boolean changed = previous == null || previous != signature;
        materialChangedCache.put(material, changed);
        return changed;
    }

    private long materialSignatureOf(PerSubmesh perSubmesh) {
        long signature = ShadowSignatures.seed();
        for (TextureHandle handle : currentTexturesOf(perSubmesh)) {
            signature = ShadowSignatures.mix(signature, handle.id());
        }
        signature = ShadowSignatures.mix(signature,
                SurfaceUniformBinder.structureRevisionOf(perSubmesh.material()));
        return ShadowSignatures.mix(signature,
                shadowMasked(perSubmesh.material(),
                        materialCache.materialUboFor(perSubmesh.material())) ? 1L : 0L);
    }

    private void refreshTextureBindingsAt(List<PerSubmesh> perSubmeshes, int index,
                                          Optional<JointPalette> jointPalette) {
        PerSubmesh existing = perSubmeshes.get(index);
        boolean masked = shadowMasked(existing.material(), materialCache.materialUboFor(existing.material()));
        boolean surfaceTexturesChanged = existing.surfaceStructureRevision()
                != SurfaceUniformBinder.structureRevisionOf(existing.material());
        if (!texturesChangedSinceCapture(existing) && masked == existing.shadowMasked() && !surfaceTexturesChanged) {
            return;
        }
        perSubmeshes.set(index, rebuildBindings(existing, masked, jointPalette));
    }

    private PerSubmesh rebuildBindings(PerSubmesh existing, boolean masked, Optional<JointPalette> jointPalette) {
        BufferHandle materialUbo = materialCache.materialUboFor(existing.material());
        BindingSetHandle freshLitBindings = backend.createBindingSet(
                buildLitBindingSetDescriptor(existing.material(), existing.classResources(),
                        existing.modelSlot(), materialUbo, jointPalette, existing.lightmapUvs()));
        BindingSetHandle freshShadowBindings = createShadowBindings(existing.material(), existing.classResources(),
                existing.modelSlot(), materialUbo, masked, jointPalette);
        backend.destroy(existing.litBindings());
        backend.destroy(existing.shadowBindings());
        ownedBindings.remove(existing.litBindings());
        ownedBindings.remove(existing.shadowBindings());
        ownedBindings.add(freshLitBindings);
        ownedBindings.add(freshShadowBindings);
        return new PerSubmesh(existing.modelSlot(), freshShadowBindings, freshLitBindings,
                existing.classResources(), existing.material(),
                captureTextures(existing.material(), existing.classResources()), masked,
                SurfaceUniformBinder.structureRevisionOf(existing.material()), existing.lightmapUvs());
    }

    private boolean texturesChangedSinceCapture(PerSubmesh perSubmesh) {
        TextureHandle[] current = currentTexturesOf(perSubmesh);
        TextureHandle[] captured = perSubmesh.capturedTextures();
        for (int i = 0; i < current.length; i++) {
            if (!current[i].equals(captured[i])) {
                return true;
            }
        }
        return false;
    }

    private TextureHandle[] currentTexturesOf(PerSubmesh perSubmesh) {
        return materialCache.resolvedTextures(perSubmesh.material());
    }

    private BindingSetDescriptor buildLitBindingSetDescriptor(Material material, MaterialClassResources classResources,
                                                              ObjectUniformSlot modelSlot, BufferHandle materialUbo,
                                                              Optional<JointPalette> jointPalette,
                                                              Optional<StorageBufferBinding> lightmapUvs) {
        return buildLitBindingSetDescriptor(material, classResources, objectTransformBindings(modelSlot),
                materialUbo, classResources.litBindingLayout(), jointPalette, lightmapUvs);
    }

    private static List<Binding> objectTransformBindings(ObjectUniformSlot modelSlot) {
        return List.of(
                new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                        new UniformBufferBinding(modelSlot.buffer(), modelSlot.byteOffset(),
                                MeshShaderBindings.OBJECT_UBO_SIZE)),
                new Binding(MeshShaderBindings.INSTANCE_SSBO_BINDING,
                        new StorageBufferBinding(modelSlot.buffer(), modelSlot.byteOffset(),
                                MeshShaderBindings.INSTANCE_TRANSFORM_BYTES)));
    }

    private static List<Binding> multiDrawTransformBindings(BufferHandle transforms, long byteSize) {
        return List.of(
                new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                        UniformBufferBinding.whole(transforms, MeshShaderBindings.OBJECT_UBO_SIZE)),
                new Binding(MeshShaderBindings.INSTANCE_SSBO_BINDING,
                        new StorageBufferBinding(transforms, 0L, byteSize)));
    }

    private static List<Binding> instanceTransformBindings(ObjectUniformSlot representativeSlot,
                                                           BufferHandle instanceBuffer, long byteOffset,
                                                           long byteSize) {
        return List.of(
                new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                        new UniformBufferBinding(representativeSlot.buffer(), representativeSlot.byteOffset(),
                                MeshShaderBindings.OBJECT_UBO_SIZE)),
                new Binding(MeshShaderBindings.INSTANCE_SSBO_BINDING,
                        new StorageBufferBinding(instanceBuffer, byteOffset, byteSize)));
    }

    private BindingSetDescriptor buildLitBindingSetDescriptor(Material material, MaterialClassResources classResources,
                                                              List<Binding> transformBindings, BufferHandle materialUbo,
                                                              BindingSetLayout layout,
                                                              Optional<JointPalette> jointPalette,
                                                              Optional<StorageBufferBinding> lightmapUvs) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                UniformBufferBinding.whole(frameUboWriter.handle(), MeshShaderBindings.FRAME_UBO_SIZE)));
        bindings.add(new Binding(MeshShaderBindings.LIGHT_SSBO_BINDING,
                StorageBufferBinding.whole(lightStorage.handle(), lightStorage.byteSize())));
        bindings.add(new Binding(MeshShaderBindings.CLUSTER_COUNT_SSBO_BINDING,
                StorageBufferBinding.whole(clusterCuller.countBuffer(), clusterCuller.countByteSize())));
        bindings.add(new Binding(MeshShaderBindings.CLUSTER_INDEX_SSBO_BINDING,
                StorageBufferBinding.whole(clusterCuller.indexBuffer(), clusterCuller.indexByteSize())));
        bindings.addAll(transformBindings);
        jointPalette.ifPresent(palette -> bindings.add(new Binding(MeshShaderBindings.JOINT_PALETTE_SSBO_BINDING,
                StorageBufferBinding.whole(palette.buffer(), palette.byteSize()))));
        if (materialCache.probeLightingActive()) {
            bindings.add(new Binding(MeshShaderBindings.PROBE_SSBO_BINDING,
                    StorageBufferBinding.whole(probeGrid.handle(), probeGrid.byteSize())));
        }
        lightmapUvs.ifPresent(binding ->
                bindings.add(new Binding(MeshShaderBindings.LIGHTMAP_UV_SSBO_BINDING, binding)));
        if (classResources.metadata().hasUniformBuffer()) {
            bindings.add(new Binding(MeshShaderBindings.MATERIAL_UBO_BINDING,
                    UniformBufferBinding.whole(materialUbo, classResources.metadata().uniformBufferSize())));
        }
        bindings.add(new Binding(MeshShaderBindings.SHADOW_MAP_BINDING,
                new SampledTextureBinding(shadowCascades.texture())));
        bindings.add(new Binding(MeshShaderBindings.OPAQUE_COLOR_BINDING,
                new SampledTextureBinding(opaqueSceneTexture(SceneTexture.OPAQUE_COLOR))));
        bindings.add(new Binding(MeshShaderBindings.OPAQUE_DEPTH_BINDING,
                new SampledTextureBinding(opaqueSceneTexture(SceneTexture.OPAQUE_DEPTH))));
        for (TextureFieldDescriptor textureField : classResources.metadata().textureFields()) {
            TextureHandle texture = classResources.metadata().readTexture(material, textureField);
            TextureHandle fallback = materialCache.defaultFor(textureField);
            bindings.add(new Binding(textureField.slotIndex(), new SampledTextureBinding(texture != null ? texture : fallback)));
        }
        bindings.add(new Binding(MeshShaderBindings.IRRADIANCE_MAP_BINDING,
                new SampledTextureBinding(environment.irradiance())));
        bindings.add(new Binding(MeshShaderBindings.PREFILTERED_MAP_BINDING,
                new SampledTextureBinding(environment.prefiltered())));
        bindings.add(new Binding(MeshShaderBindings.BRDF_LUT_BINDING,
                new SampledTextureBinding(environment.brdfLut())));
        bindings.add(new Binding(MeshShaderBindings.SPOT_SHADOW_ATLAS_BINDING,
                new SampledTextureBinding(spotShadows.texture())));
        bindings.add(new Binding(MeshShaderBindings.POINT_SHADOW_ATLAS_BINDING,
                new SampledTextureBinding(pointShadows.texture())));
        surfaceUniforms.appendBindings(bindings, material, classResources.surfaceUniforms());
        return new BindingSetDescriptor(layout, bindings);
    }

    private static final class PendingPose {
        private GameObject gameObject;
        private Animator animator;
        private UploadedMesh mesh;
        private RenderableMesh renderable;
        private JointPalette palette;
        private BindPose bindPose;
        private Skeleton skeleton;

        private void adopt(GameObject owner, Animator source, UploadedMesh sourceMesh,
                           RenderableMesh sourceRenderable, JointPalette sourcePalette,
                           BindPose sourceBindPose, Skeleton sourceSkeleton) {
            gameObject = owner;
            animator = source;
            mesh = sourceMesh;
            renderable = sourceRenderable;
            palette = sourcePalette;
            bindPose = sourceBindPose;
            skeleton = sourceSkeleton;
        }

        private void clear() {
            gameObject = null;
            animator = null;
            mesh = null;
            renderable = null;
            palette = null;
            bindPose = null;
            skeleton = null;
        }
    }

    private static final class RenderableMesh {
        private final UploadedMesh mesh;
        private final List<PerSubmesh> submeshes;
        private final Optional<JointPalette> jointPalette;
        private final Optional<DeformedMesh> deformedMesh;
        private int lastSeenFrame = -1;

        private RenderableMesh(UploadedMesh mesh, List<PerSubmesh> submeshes,
                               Optional<JointPalette> jointPalette, Optional<DeformedMesh> deformedMesh) {
            this.mesh = mesh;
            this.submeshes = submeshes;
            this.jointPalette = jointPalette;
            this.deformedMesh = deformedMesh;
        }

        Optional<DeformedMesh> deformedMesh() {
            return deformedMesh;
        }

        boolean skinnedPipelines() {
            return mesh.skinned() && deformedMesh.isEmpty();
        }

        Optional<JointPalette> pipelinePalette() {
            return deformedMesh.isPresent() ? Optional.empty() : jointPalette;
        }

        UploadedSubmesh drawSubmesh(int slot) {
            return deformedMesh.map(deformed -> deformed.submesh(slot)).orElseGet(() -> mesh.submeshes().get(slot));
        }

        UploadedMesh mesh() {
            return mesh;
        }

        List<PerSubmesh> submeshes() {
            return submeshes;
        }

        Optional<JointPalette> jointPalette() {
            return jointPalette;
        }

        JointPalette jointPaletteOrNull() {
            return jointPalette.orElse(null);
        }
    }

    private static final class JointPalette {
        private final BufferHandle buffer;
        private final long byteSize;
        private final SkeletonPose pose;
        private final SkeletonPose previousPose;
        private final SkeletonPose layerPose;
        private final Matrix4f[] skinningMatrices;
        private final ByteBuffer packBuffer;
        private final Vector4f rowScratch = new Vector4f();
        private final Set<AnimationLayer> warnedLayers = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean checksumMismatchLogged;
        private final AnimatedBounds animatedBounds = new AnimatedBounds();
        private long lastAnimationGeneration = -2L;
        private int phase;

        private JointPalette(BufferHandle buffer, long byteSize, int jointCount) {
            this.buffer = buffer;
            this.byteSize = byteSize;
            this.pose = new SkeletonPose(jointCount);
            this.previousPose = new SkeletonPose(jointCount);
            this.layerPose = new SkeletonPose(jointCount);
            this.skinningMatrices = createIdentityMatrices(jointCount);
            this.packBuffer = BufferUtils.createByteBuffer((int) byteSize);
        }

        private BufferHandle buffer() {
            return buffer;
        }

        private long byteSize() {
            return byteSize;
        }
    }

    private static Matrix4f[] createIdentityMatrices(int jointCount) {
        Matrix4f[] matrices = new Matrix4f[jointCount];
        for (int index = 0; index < jointCount; index++) {
            matrices[index] = new Matrix4f();
        }
        return matrices;
    }
}
