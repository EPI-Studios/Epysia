package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipSampler;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.animation.SkeletonPose;
import fr.epistudio.epysia.animation.SkinningPalette;
import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.SpotLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.profiling.FrameProfiler;
import fr.epistudio.epysia.render.ProfiledRenderSystem;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.environment.Environment;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MeshRenderSystem implements RenderSystem, ProfiledRenderSystem {

    private FrameProfiler profiler = new FrameProfiler();

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

    private final ShaderWatcher shaderWatcher;
    private final ShadowStatistics shadowStatistics = new ShadowStatistics();
    private final Logger logger;
    private final SurfaceTimeDependence surfaceTimeDependence;
    private boolean shadowCachingEnabled = true;
    private boolean shadowSplitEnabled = true;
    private final FrameUboWriter frameUboWriter = new FrameUboWriter();
    private final LightStorage lightStorage = new LightStorage();
    private final ClusterLightCuller clusterCuller = new ClusterLightCuller();
    private boolean clusteringEnabled = true;
    private final CascadedShadowMaps shadowCascades;
    private final SpotShadowAtlas spotShadows;
    private final PointShadowAtlas pointShadows;
    private final MaterialPipelineCache materialCache;
    private final SurfaceUniformBinder surfaceUniforms;
    private final InstancedMeshPass instancedPass;
    private final MeshInstanceBatches instanceBatches = new MeshInstanceBatches();
    private final MaterialStateDigests materialStates = new MaterialStateDigests();
    private boolean instancingEnabled = true;
    private int batchesThisFrame;
    private int instancedBatchesThisFrame;
    private final FrustumCuller culler = new FrustumCuller();

    private final Map<MeshRenderer, RenderableMesh> objectResources = new IdentityHashMap<>();
    private final Map<BufferHandle, Long> objectUboTransformHashes = new HashMap<>();
    private final Vector3f scratchCasterMin = new Vector3f();
    private final Vector3f scratchCasterMax = new Vector3f();
    private final Set<MeshRenderer> renderersSeenThisFrame =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<MeshRenderer> loggedSkinnedExclusions =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();
    private final List<BindingSetHandle> ownedBindings = new ArrayList<>();
    private final List<Light> activeLights = new ArrayList<>(64);
    private final ByteBuffer scratchObjectUbo = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
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
    private LitMaterial fallback;

    private final ClipSampler clipSampler = new ClipSampler();
    private RenderBackend backend;
    private int culledThisFrame;
    private int submittedThisFrame;
    private long startNanos;
    private long lastFrameNanos;
    private float frameDeltaSeconds;

    public MeshRenderSystem(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger) {
        this.logger = logger;
        this.shaderWatcher = shaderWatcher;
        this.surfaceTimeDependence = new SurfaceTimeDependence(shaderLoader, logger);
        this.shadowCascades = new CascadedShadowMaps(shaderLoader, shaderWatcher, logger,
                shadowStatistics, this::invalidateShadowCaches);
        this.spotShadows = new SpotShadowAtlas(shaderLoader, shaderWatcher, logger,
                shadowStatistics, this::invalidateShadowCaches);
        this.pointShadows = new PointShadowAtlas(shaderLoader, shaderWatcher, logger,
                shadowStatistics, this::invalidateShadowCaches);
        this.materialCache = new MaterialPipelineCache(shaderLoader, shaderWatcher, logger);
        this.surfaceUniforms = new SurfaceUniformBinder(logger);
        this.instancedPass = new InstancedMeshPass(shaderLoader);
        this.environment = new Environment(shaderLoader);
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.startNanos = System.nanoTime();
        this.lastFrameNanos = this.startNanos;
        shadowCascades.initialize(backend);
        configurer.bindStagePreparation(RenderPasses.OPAQUE_3D, shadowCascades::render);
        spotShadows.initialize(backend, shadowCascades.cascadeUbo());
        configurer.bindStagePreparation(RenderPasses.OPAQUE_3D, spotShadows::render);
        pointShadows.initialize(backend, shadowCascades.cascadeUbo());
        configurer.bindStagePreparation(RenderPasses.OPAQUE_3D, pointShadows::render);
        materialCache.initialize(backend);
        surfaceUniforms.initialize(backend);
        frameUboWriter.initialize(backend);
        lightStorage.initialize(backend);
        clusterCuller.initialize(backend);
        instancedPass.initialize(backend, frameUboWriter.handle(), lightStorage);
        instanceBatches.initialize(backend, this::createInstanceBindingSet);
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

    public InstancedMeshPass instancedPass() {
        return instancedPass;
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
            return;
        }
        Optional<DirectionalLight> primaryDirectional = findComponent(scene, DirectionalLight.class);
        resolveSunDirection(primaryDirectional);
        environment.prepareFrame(scratchSunDirection);
        gatherLights(scene, primaryDirectional);
        mark = markSection("mesh/lights", mark);
        shadowStatistics.beginFrame();
        long sceneModificationCount = scene.modificationCount();
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
                clusterCuller, clusteringEnabled, alpha);
        lightStorage.update(activeLights, spotShadows, pointShadows);
        mark = markSection("mesh/uniforms", mark);
        materialCache.beginFrame();
        surfaceUniforms.beginFrame();
        renderersSeenThisFrame.clear();
        camera.position(scratchCameraPosition, alpha);
        culler.setProjection(camera.viewProjection(alpha));
        culledThisFrame = 0;
        submittedThisFrame = 0;
        instanceBatches.beginFrame();
        for (GameObject gameObject : scene.gameObjects()) {
            submitMeshDraws(gameObject, frame, alpha);
        }
        mark = markSection("mesh/objectLoop", mark);
        flushInstanceBatches(frame);
        instancedPass.collect(scene, frame);
        environment.collectSky(camera, scratchSunDirection, frame, alpha);
        purgeOrphanRenderers();
        markSection("mesh/flush", mark);
    }

    public Environment environment() {
        return environment;
    }

    public MeshRenderSystem setShadowDistance(float distance) {
        this.shadowDistance = distance;
        return this;
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
        for (BindingSetHandle binding : ownedBindings) {
            backend.destroy(binding);
        }
        for (BufferHandle buffer : ownedBuffers) {
            backend.destroy(buffer);
        }
        ownedBindings.clear();
        ownedBuffers.clear();
        objectResources.clear();
        renderersSeenThisFrame.clear();
        loggedSkinnedExclusions.clear();
        instanceBatches.shutdown();
        instancedPass.shutdown();
        surfaceUniforms.shutdown();
        materialCache.shutdown();
        shadowCascades.shutdown();
        spotShadows.shutdown();
        pointShadows.shutdown();
        frameUboWriter.shutdown();
        lightStorage.shutdown();
        clusterCuller.shutdown();
        environment.shutdown();
    }

    private void gatherLights(Scene scene, Optional<DirectionalLight> primary) {
        activeLights.clear();
        primary.ifPresent(activeLights::add);
        collectLights(scene, primary, true);
        collectLights(scene, primary, false);
    }

    private void collectLights(Scene scene, Optional<DirectionalLight> primary, boolean directional) {
        for (GameObject gameObject : scene.gameObjects()) {
            Light light = gameObject.getComponentOrNull(Light.class);
            if (light == null || (primary.isPresent() && light == primary.get())) {
                continue;
            }
            if (light instanceof DirectionalLight != directional) {
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

    private <T extends IComponent> Optional<T> findComponent(Scene scene, Class<T> componentClass) {
        for (GameObject gameObject : scene.gameObjects()) {
            T component = gameObject.getComponentOrNull(componentClass);
            if (component != null) {
                return Optional.of(component);
            }
        }
        return Optional.empty();
    }

    private void submitMeshDraws(GameObject gameObject, FrameBuilder frame, float alpha) {
        MeshRenderer renderer = gameObject.getComponentOrNull(MeshRenderer.class);
        Transform3D transformComponent = gameObject.getComponentOrNull(Transform3D.class);
        if (renderer == null || transformComponent == null) {
            return;
        }
        Optional<UploadedMesh> meshOpt = renderer.mesh();
        if (meshOpt.isEmpty()) {
            return;
        }
        UploadedMesh mesh = meshOpt.get();
        renderersSeenThisFrame.add(renderer);
        boolean excludedFromShadows = mesh.skinned() || mesh.vertexColored();
        if (mesh.skinned()) {
            logExclusionOnce(gameObject, renderer, "Skinned");
        } else if (mesh.vertexColored()) {
            logExclusionOnce(gameObject, renderer, "Vertex-colored");
        }
        RenderableMesh renderable = resolvePerSubmeshes(renderer, mesh);
        List<PerSubmesh> perSubmeshes = renderable.submeshes();
        refreshStalePerSubmeshes(renderer, mesh, perSubmeshes, renderable.jointPalette());
        if (mesh.skinned()) {
            renderable.jointPalette().ifPresent(palette -> updateAnimatedPalette(gameObject, mesh, palette));
        }
        Matrix4f modelMatrix = transformComponent.worldMatrix(alpha);
        culler.computeWorldBounds(mesh.localBounds(), modelMatrix, scratchCasterMin, scratchCasterMax);
        boolean visible = mesh.localBounds() == null
                || !culler.isCulled(scratchCasterMin, scratchCasterMax);
        long depthBits = viewDepthBits(modelMatrix);
        long transformHash = ShadowSignatures.mixMatrix(ShadowSignatures.seed(), modelMatrix);
        if (visible) {
            submittedThisFrame++;
        } else {
            culledThisFrame++;
        }
        for (int i = 0; i < mesh.submeshes().size(); i++) {
            UploadedSubmesh submesh = mesh.submeshes().get(i);
            PerSubmesh perSubmesh = perSubmeshes.get(i);
            materialCache.writeMaterialUboIfNeeded(perSubmesh.material(), perSubmesh.classResources());
            surfaceUniforms.writeIfNeeded(perSubmesh.material(), perSubmesh.classResources().surfaceUniforms());
            if (!excludedFromShadows && batchable(perSubmesh)
                    && instanceBatches.add(submesh, perSubmesh,
                            materialStates.snapshotFor(perSubmesh, materialCache, surfaceUniforms),
                            modelMatrix, depthBits, visible, scratchCasterMin, scratchCasterMax)) {
                continue;
            }
            writeObjectUboIfChanged(perSubmesh.modelUbo(), modelMatrix, transformHash);
            submitSubmesh(frame, submesh, perSubmesh, depthBits, transformHash, visible, excludedFromShadows);
        }
    }

    private void logExclusionOnce(GameObject gameObject, MeshRenderer renderer, String reason) {
        if (!loggedSkinnedExclusions.add(renderer)) {
            return;
        }
        logger.info(reason + " mesh '" + gameObject.name()
                + "' excluded from shadow casting and picking this milestone.");
    }

    private boolean batchable(PerSubmesh perSubmesh) {
        return instancingEnabled
                && !perSubmesh.material().transparent()
                && perSubmesh.classResources().supportsInstancing();
    }

    private void flushInstanceBatches(FrameBuilder frame) {
        batchesThisFrame = instanceBatches.activeBatches().size();
        instancedBatchesThisFrame = 0;
        for (MeshInstanceBatch batch : instanceBatches.activeBatches()) {
            if (batch.pendingCount() == 1) {
                submitBatchAsSingleObject(frame, batch);
                continue;
            }
            instanceBatches.upload(batch);
            submitInstancedBatch(frame, batch);
            instancedBatchesThisFrame++;
        }
    }

    private void submitBatchAsSingleObject(FrameBuilder frame, MeshInstanceBatch batch) {
        PerSubmesh perSubmesh = batch.representative();
        writeObjectUbo(perSubmesh.modelUbo(), batch.firstModel());
        long transformHash = ShadowSignatures.mixMatrix(ShadowSignatures.seed(), batch.firstModel());
        submitSubmesh(frame, batch.submesh(), perSubmesh, batch.minDepthBits(), transformHash,
                batch.visibleCount() == 1, false);
    }

    private void submitInstancedBatch(FrameBuilder frame, MeshInstanceBatch batch) {
        PerSubmesh perSubmesh = batch.representative();
        PipelineHandle pipeline = perSubmesh.classResources().pipeline();
        String surfacePath = MaterialPipelineCache.surfaceShaderPathOf(perSubmesh.material());
        submitInstancedShadowCasters(batch, surfacePath);
        if (batch.visibleCount() == 0) {
            return;
        }
        long opaqueKey = (pipeline.id() << 32) | batch.minDepthBits();
        frame.submit(RenderPasses.OPAQUE_3D, new DrawCommand(pipeline, batch.submesh().handle(),
                batch.litBindings(), opaqueKey, batch.visibleCount()));
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
        int count = batch.instanceCount();
        if (shadowCascades.cascadesActive()) {
            shadowCascades.submitCaster(new DrawCommand(
                    shadowCascades.pipelineFor(surfacePath, perSubmesh.shadowMasked(), frozen),
                    batch.submesh().handle(), batch.shadowBindings(), 0L, count),
                    identity, signature, timeAnimated, batch.boundsMin(), batch.boundsMax());
        }
        if (spotShadows.activeCount() > 0) {
            spotShadows.submitCaster(new DrawCommand(spotShadows.pipelineFor(surfacePath, frozen),
                    batch.submesh().handle(), batch.shadowBindings(), 0L, count),
                    identity, signature, timeAnimated);
        }
        if (pointShadows.activeCount() > 0) {
            pointShadows.submitCaster(new DrawCommand(pointShadows.pipelineFor(surfacePath, frozen),
                    batch.submesh().handle(), batch.shadowBindings(), 0L, count),
                    identity, signature, timeAnimated);
        }
    }

    private static long instancedCasterSignature(MeshInstanceBatch batch, String surfacePath, boolean frozen) {
        PerSubmesh perSubmesh = batch.representative();
        long signature = ShadowSignatures.mix(batch.transformHash(), batch.submesh().handle().id());
        signature = ShadowSignatures.mix(signature, batch.shadowBindings().id());
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
                               long depthBits, long transformHash, boolean visible, boolean excludedFromShadows) {
        PipelineHandle pipeline = perSubmesh.classResources().pipeline();
        if (perSubmesh.material().transparent()) {
            if (!visible) {
                return;
            }
            long backToFrontKey = 0xFFFFFFFFL - depthBits;
            frame.submit(RenderPasses.TRANSPARENT_3D, new DrawCommand(pipeline, submesh.handle(), perSubmesh.litBindings(), backToFrontKey, 1));
            return;
        }
        if (!excludedFromShadows) {
            String surfacePath = MaterialPipelineCache.surfaceShaderPathOf(perSubmesh.material());
            submitShadowCasters(submesh, perSubmesh, surfacePath, transformHash);
        }
        if (!visible) {
            return;
        }
        long opaqueKey = (pipeline.id() << 32) | depthBits;
        frame.submit(RenderPasses.OPAQUE_3D, new DrawCommand(pipeline, submesh.handle(), perSubmesh.litBindings(), opaqueKey, 1));
    }

    private void submitShadowCasters(UploadedSubmesh submesh, PerSubmesh perSubmesh,
                                     String surfacePath, long transformHash) {
        boolean frozen = shadowTimeFrozen(perSubmesh.material(), surfacePath);
        boolean timeAnimated = shadowTimeAnimated(perSubmesh.material(), surfacePath);
        if (timeAnimated) {
            shadowStatistics.recordAnimatedCaster();
        }
        long identity = ShadowSignatures.mix(
                ShadowSignatures.mix(SINGLE_CASTER_DOMAIN, submesh.handle().id()),
                perSubmesh.shadowBindings().id());
        long signature = casterSignature(submesh, perSubmesh, surfacePath, transformHash, frozen);
        if (shadowCascades.cascadesActive()) {
            PipelineHandle shadowPipeline =
                    shadowCascades.pipelineFor(surfacePath, perSubmesh.shadowMasked(), frozen);
            shadowCascades.submitCaster(new DrawCommand(shadowPipeline, submesh.handle(),
                    perSubmesh.shadowBindings(), 0L, 1), identity, signature, timeAnimated,
                    scratchCasterMin, scratchCasterMax);
        }
        if (spotShadows.activeCount() > 0) {
            spotShadows.submitCaster(new DrawCommand(spotShadows.pipelineFor(surfacePath, frozen), submesh.handle(),
                    perSubmesh.shadowBindings(), 0L, 1), identity, signature, timeAnimated);
        }
        if (pointShadows.activeCount() > 0) {
            pointShadows.submitCaster(new DrawCommand(pointShadows.pipelineFor(surfacePath, frozen), submesh.handle(),
                    perSubmesh.shadowBindings(), 0L, 1), identity, signature, timeAnimated);
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

    private long viewDepthBits(Matrix4f modelMatrix) {
        float dx = modelMatrix.m30() - scratchCameraPosition.x;
        float dy = modelMatrix.m31() - scratchCameraPosition.y;
        float dz = modelMatrix.m32() - scratchCameraPosition.z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        return Float.floatToIntBits(distanceSquared) & 0xFFFFFFFFL;
    }

    private void writeObjectUboIfChanged(BufferHandle ubo, Matrix4f model, long transformHash) {
        Long previous = objectUboTransformHashes.get(ubo);
        if (previous != null && previous == transformHash) {
            return;
        }
        objectUboTransformHashes.put(ubo, transformHash);
        writeObjectUbo(ubo, model);
    }

    private void writeObjectUbo(BufferHandle ubo, Matrix4f model) {
        scratchObjectUbo.clear();
        model.get(0, scratchObjectUbo);
        model.normal(scratchNormalMatrix);
        scratchNormalMatrix.get(64, scratchObjectUbo);
        scratchObjectUbo.position(0);
        scratchObjectUbo.limit(MeshShaderBindings.OBJECT_UBO_SIZE);
        backend.writeBuffer(ubo, scratchObjectUbo, 0L);
    }

    private void purgeOrphanRenderers() {
        if (objectResources.size() == renderersSeenThisFrame.size()) {
            return;
        }
        Iterator<Map.Entry<MeshRenderer, RenderableMesh>> iterator = objectResources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MeshRenderer, RenderableMesh> entry = iterator.next();
            if (renderersSeenThisFrame.contains(entry.getKey())) {
                continue;
            }
            destroyPerSubmeshes(entry.getValue().submeshes());
            entry.getValue().jointPalette().ifPresent(this::destroyJointPalette);
            loggedSkinnedExclusions.remove(entry.getKey());
            iterator.remove();
        }
    }

    private RenderableMesh resolvePerSubmeshes(MeshRenderer renderer, UploadedMesh mesh) {
        RenderableMesh cached = objectResources.get(renderer);
        if (cached != null && cached.mesh() == mesh) {
            return cached;
        }
        if (cached != null) {
            destroyPerSubmeshes(cached.submeshes());
            cached.jointPalette().ifPresent(this::destroyJointPalette);
        }
        Optional<JointPalette> palette = createJointPaletteIfSkinned(mesh);
        RenderableMesh rebuilt = new RenderableMesh(mesh, createPerSubmeshes(renderer, mesh, palette), palette);
        objectResources.put(renderer, rebuilt);
        return rebuilt;
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
        return Optional.of(new JointPalette(buffer, byteSize, jointCount));
    }

    private void updateAnimatedPalette(GameObject gameObject, UploadedMesh mesh, JointPalette palette) {
        Animator animator = gameObject.getComponentOrNull(Animator.class);
        if (animator == null || !animator.isPlaying() || animator.resolvedClip().isEmpty()) {
            return;
        }
        Clip clip = animator.resolvedClip().get();
        Skeleton skeleton = mesh.skeleton().orElseThrow(() ->
                new EpysiaException("Skinned mesh missing skeleton during animation."));
        if (clip.skeletonChecksum() != skeleton.nameChecksum()) {
            logChecksumMismatchOnce(gameObject, clip, skeleton, palette);
            return;
        }
        animator.advance(frameDeltaSeconds, clip.durationSeconds());
        clipSampler.sample(clip, skeleton, animator.currentTimeSeconds(), palette.pose);
        palette.pose.computeSkinningMatrices(skeleton, palette.skinningMatrices);
        SkinningPalette.pack(palette.skinningMatrices, palette.packBuffer, palette.rowScratch);
        backend.writeBuffer(palette.buffer, palette.packBuffer, 0L);
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
        backend.destroy(perSubmesh.litBindings());
        backend.destroy(perSubmesh.shadowBindings());
        backend.destroy(perSubmesh.modelUbo());
        ownedBindings.remove(perSubmesh.litBindings());
        ownedBindings.remove(perSubmesh.shadowBindings());
        ownedBuffers.remove(perSubmesh.modelUbo());
        objectUboTransformHashes.remove(perSubmesh.modelUbo());
    }

    private Material resolveMaterial(MeshRenderer renderer, int slot) {
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

    private List<PerSubmesh> createPerSubmeshes(MeshRenderer renderer, UploadedMesh mesh,
                                                Optional<JointPalette> jointPalette) {
        List<PerSubmesh> result = new ArrayList<>(mesh.submeshes().size());
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            result.add(createPerSubmesh(renderer, submesh, mesh.skinned(), mesh.vertexColored(), jointPalette));
        }
        return result;
    }

    private PerSubmesh createPerSubmesh(MeshRenderer renderer, UploadedSubmesh submesh, boolean skinned,
                                        boolean colored, Optional<JointPalette> jointPalette) {
        Material material = resolveMaterial(renderer, submesh.materialSlot());
        MaterialClassResources classResources = materialCache.classResourcesFor(material, skinned, colored);
        BufferHandle materialUbo = materialCache.ensureMaterialUbo(material, classResources);
        ByteBuffer empty = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
        BufferHandle modelUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, empty));
        ownedBuffers.add(modelUbo);
        boolean shadowMasked = shadowMasked(material, materialUbo);
        BindingSetHandle shadowBindings = createShadowBindings(material, classResources, modelUbo, materialUbo, shadowMasked);
        BindingSetHandle litBindings = backend.createBindingSet(
                buildLitBindingSetDescriptor(material, classResources, modelUbo, materialUbo, jointPalette));
        ownedBindings.add(shadowBindings);
        ownedBindings.add(litBindings);
        return new PerSubmesh(modelUbo, shadowBindings, litBindings, classResources, material,
                captureTextures(material, classResources), shadowMasked,
                SurfaceUniformBinder.structureRevisionOf(material));
    }

    private static boolean shadowMasked(Material material, BufferHandle materialUbo) {
        return materialUbo != null && material instanceof LitMaterial lit && lit.alphaCutoff > 0.0f;
    }

    private BindingSetHandle createShadowBindings(Material material, MaterialClassResources classResources,
                                                  BufferHandle modelUbo, BufferHandle materialUbo, boolean masked) {
        return createShadowBindings(material, classResources, objectTransformBindings(modelUbo), materialUbo, masked,
                shadowCascades.bindingLayout(), shadowCascades.maskedBindingLayout());
    }

    private BindingSetHandle createShadowBindings(Material material, MaterialClassResources classResources,
                                                  List<Binding> transformBindings, BufferHandle materialUbo, boolean masked,
                                                  BindingSetLayout layout, BindingSetLayout maskedLayout) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                UniformBufferBinding.whole(frameUboWriter.handle(), MeshShaderBindings.FRAME_UBO_SIZE)));
        bindings.addAll(transformBindings);
        bindings.add(new Binding(MeshShaderBindings.CASCADE_UBO_BINDING,
                UniformBufferBinding.whole(shadowCascades.cascadeUbo(), MeshShaderBindings.CASCADE_UBO_SIZE)));
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
                                                      long byteSize, boolean shadow) {
        List<Binding> transformBindings =
                instanceTransformBindings(perSubmesh.modelUbo(), instanceBuffer, byteSize);
        Material material = perSubmesh.material();
        MaterialClassResources classResources = perSubmesh.classResources();
        BufferHandle materialUbo = materialCache.materialUboFor(material);
        if (!shadow) {
            return backend.createBindingSet(buildLitBindingSetDescriptor(material, classResources, transformBindings,
                    materialUbo, classResources.litBindingLayout(), Optional.empty()));
        }
        return createShadowBindings(material, classResources, transformBindings, materialUbo,
                perSubmesh.shadowMasked(), shadowCascades.bindingLayout(),
                shadowCascades.maskedBindingLayout());
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

    private void refreshStalePerSubmeshes(MeshRenderer renderer, UploadedMesh mesh, List<PerSubmesh> perSubmeshes,
                                          Optional<JointPalette> jointPalette) {
        boolean skinned = mesh.skinned();
        boolean colored = mesh.vertexColored();
        for (int i = 0; i < perSubmeshes.size(); i++) {
            PerSubmesh existing = perSubmeshes.get(i);
            UploadedSubmesh submesh = mesh.submeshes().get(i);
            Material current = resolveMaterial(renderer, submesh.materialSlot());
            if (materialOrPipelineChanged(current, existing, skinned, colored)) {
                destroyPerSubmesh(existing);
                perSubmeshes.set(i, createPerSubmesh(renderer, submesh, skinned, colored, jointPalette));
                continue;
            }
            refreshTextureBindingsAt(perSubmeshes, i, jointPalette);
        }
    }

    private boolean materialOrPipelineChanged(Material current, PerSubmesh existing, boolean skinned, boolean colored) {
        return current != existing.material()
                || materialCache.classResourcesFor(current, skinned, colored) != existing.classResources();
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
                        existing.modelUbo(), materialUbo, jointPalette));
        BindingSetHandle freshShadowBindings = createShadowBindings(existing.material(), existing.classResources(),
                existing.modelUbo(), materialUbo, masked);
        backend.destroy(existing.litBindings());
        backend.destroy(existing.shadowBindings());
        ownedBindings.remove(existing.litBindings());
        ownedBindings.remove(existing.shadowBindings());
        ownedBindings.add(freshLitBindings);
        ownedBindings.add(freshShadowBindings);
        return new PerSubmesh(existing.modelUbo(), freshShadowBindings, freshLitBindings,
                existing.classResources(), existing.material(),
                captureTextures(existing.material(), existing.classResources()), masked,
                SurfaceUniformBinder.structureRevisionOf(existing.material()));
    }

    private boolean texturesChangedSinceCapture(PerSubmesh perSubmesh) {
        List<TextureFieldDescriptor> textureFields = perSubmesh.classResources().metadata().textureFields();
        TextureHandle[] captured = perSubmesh.capturedTextures();
        for (int i = 0; i < textureFields.size(); i++) {
            TextureHandle current = perSubmesh.classResources().metadata().readTexture(perSubmesh.material(), textureFields.get(i));
            if (current == null) {
                current = materialCache.defaultFor(textureFields.get(i));
            }
            if (!current.equals(captured[i])) {
                return true;
            }
        }
        return false;
    }

    private BindingSetDescriptor buildLitBindingSetDescriptor(Material material, MaterialClassResources classResources,
                                                              BufferHandle modelUbo, BufferHandle materialUbo,
                                                              Optional<JointPalette> jointPalette) {
        return buildLitBindingSetDescriptor(material, classResources, objectTransformBindings(modelUbo),
                materialUbo, classResources.litBindingLayout(), jointPalette);
    }

    private static List<Binding> objectTransformBindings(BufferHandle modelUbo) {
        return List.of(
                new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                        UniformBufferBinding.whole(modelUbo, MeshShaderBindings.OBJECT_UBO_SIZE)),
                new Binding(MeshShaderBindings.INSTANCE_SSBO_BINDING,
                        new StorageBufferBinding(modelUbo, 0L, MeshShaderBindings.INSTANCE_TRANSFORM_BYTES)));
    }

    private static List<Binding> instanceTransformBindings(BufferHandle representativeUbo,
                                                           BufferHandle instanceBuffer, long byteSize) {
        return List.of(
                new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                        UniformBufferBinding.whole(representativeUbo, MeshShaderBindings.OBJECT_UBO_SIZE)),
                new Binding(MeshShaderBindings.INSTANCE_SSBO_BINDING,
                        new StorageBufferBinding(instanceBuffer, 0L, byteSize)));
    }

    private BindingSetDescriptor buildLitBindingSetDescriptor(Material material, MaterialClassResources classResources,
                                                              List<Binding> transformBindings, BufferHandle materialUbo,
                                                              BindingSetLayout layout,
                                                              Optional<JointPalette> jointPalette) {
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
        if (classResources.metadata().hasUniformBuffer()) {
            bindings.add(new Binding(MeshShaderBindings.MATERIAL_UBO_BINDING,
                    UniformBufferBinding.whole(materialUbo, classResources.metadata().uniformBufferSize())));
        }
        bindings.add(new Binding(MeshShaderBindings.SHADOW_MAP_BINDING,
                new SampledTextureBinding(shadowCascades.texture())));
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

    private record RenderableMesh(UploadedMesh mesh, List<PerSubmesh> submeshes,
                                  Optional<JointPalette> jointPalette) {
    }

    private static final class JointPalette {

        private final BufferHandle buffer;
        private final long byteSize;
        private final SkeletonPose pose;
        private final Matrix4f[] skinningMatrices;
        private final ByteBuffer packBuffer;
        private final Vector4f rowScratch = new Vector4f();
        private boolean checksumMismatchLogged;

        private JointPalette(BufferHandle buffer, long byteSize, int jointCount) {
            this.buffer = buffer;
            this.byteSize = byteSize;
            this.pose = new SkeletonPose(jointCount);
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
