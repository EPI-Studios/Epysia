package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.components.Light;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.Stage;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.TextureHandle;
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
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MeshRenderSystem implements RenderSystem {

    private static final String SHADOW_MASK_ALBEDO_FIELD = "albedo";

    private final ShaderWatcher shaderWatcher;
    private final FrameUboWriter frameUboWriter = new FrameUboWriter();
    private final CascadedShadowMaps shadowCascades;
    private final MaterialPipelineCache materialCache;
    private final InstancedMeshPass instancedPass;
    private final FrustumCuller culler = new FrustumCuller();

    private final Map<MeshRenderer, RenderableMesh> objectResources = new IdentityHashMap<>();
    private final Set<MeshRenderer> renderersSeenThisFrame =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();
    private final List<BindingSetHandle> ownedBindings = new ArrayList<>();
    private final List<Light> activeLights = new ArrayList<>(MeshShaderBindings.MAX_LIGHTS * 2);
    private final ByteBuffer scratchObjectUbo = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
    private final Matrix4f scratchNormalMatrix = new Matrix4f();
    private final Vector3f scratchSunDirection = new Vector3f();
    private final Vector3f scratchLightDirection = new Vector3f();
    private final Vector3f scratchCameraPosition = new Vector3f();
    private final Environment environment;
    private float shadowDistance = 60.0f;
    private LitMaterial fallback;

    private RenderBackend backend;
    private int culledThisFrame;
    private long startNanos;

    public MeshRenderSystem(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger) {
        this.shaderWatcher = shaderWatcher;
        this.shadowCascades = new CascadedShadowMaps(shaderLoader, shaderWatcher, logger);
        this.materialCache = new MaterialPipelineCache(shaderLoader, shaderWatcher, logger);
        this.instancedPass = new InstancedMeshPass(shaderLoader);
        this.environment = new Environment(shaderLoader);
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.startNanos = System.nanoTime();
        shadowCascades.initialize(backend);
        configurer.bindStagePreparation(Stage.OPAQUE_3D, shadowCascades::render);
        materialCache.initialize(backend);
        frameUboWriter.initialize(backend);
        instancedPass.initialize(backend, frameUboWriter.handle());
        environment.initialize(backend);
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        shaderWatcher.poll();
        Camera3D camera = context.primaryCamera().orElse(null);
        if (camera == null) {
            return;
        }
        Optional<DirectionalLight> primaryDirectional = findComponent(scene, DirectionalLight.class);
        resolveSunDirection(primaryDirectional);
        environment.prepareFrame(scratchSunDirection);
        gatherLights(scene, primaryDirectional);
        shadowCascades.beginFrame();
        float alpha = context.interpolationAlpha();
        if (primaryDirectional.isPresent()) {
            primaryDirectional.get().direction(scratchLightDirection).normalize();
            shadowCascades.update(camera, scratchLightDirection, shadowDistance, alpha);
        }
        float timeSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
        frameUboWriter.write(camera, primaryDirectional, activeLights, timeSeconds,
                environment.settings().ambientIntensity(), shadowCascades, alpha);
        materialCache.beginFrame();
        renderersSeenThisFrame.clear();
        camera.position(scratchCameraPosition, alpha);
        culler.setProjection(camera.viewProjection(alpha));
        culledThisFrame = 0;
        for (GameObject gameObject : scene.gameObjects()) {
            submitMeshDraws(gameObject, frame, alpha);
        }
        instancedPass.collect(scene, frame);
        environment.collectSky(camera, scratchSunDirection, frame, alpha);
        purgeOrphanRenderers();
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
        instancedPass.shutdown();
        materialCache.shutdown();
        shadowCascades.shutdown();
        frameUboWriter.shutdown();
        environment.shutdown();
    }

    private void gatherLights(Scene scene, Optional<DirectionalLight> primary) {
        activeLights.clear();
        primary.ifPresent(activeLights::add);
        for (GameObject gameObject : scene.gameObjects()) {
            Light light = gameObject.getComponent(Light.class).orElse(null);
            boolean isPrimary = primary.isPresent() && light == primary.get();
            if (light != null && !isPrimary && activeLights.size() < MeshShaderBindings.MAX_LIGHTS) {
                activeLights.add(light);
            }
        }
    }

    private <T extends IComponent> Optional<T> findComponent(Scene scene, Class<T> componentClass) {
        for (GameObject gameObject : scene.gameObjects()) {
            Optional<T> component = gameObject.getComponent(componentClass);
            if (component.isPresent()) {
                return component;
            }
        }
        return Optional.empty();
    }

    private void submitMeshDraws(GameObject gameObject, FrameBuilder frame, float alpha) {
        Optional<MeshRenderer> rendererOpt = gameObject.getComponent(MeshRenderer.class);
        Optional<Transform3D> transformOpt = gameObject.getComponent(Transform3D.class);
        if (rendererOpt.isEmpty() || transformOpt.isEmpty()) {
            return;
        }
        MeshRenderer renderer = rendererOpt.get();
        Optional<UploadedMesh> meshOpt = renderer.mesh();
        if (meshOpt.isEmpty()) {
            return;
        }
        UploadedMesh mesh = meshOpt.get();
        renderersSeenThisFrame.add(renderer);
        List<PerSubmesh> perSubmeshes = resolvePerSubmeshes(renderer, mesh);
        refreshStaleTextureBindings(perSubmeshes);
        Matrix4f modelMatrix = transformOpt.get().worldMatrix(alpha);
        if (culler.isCulled(mesh.localBounds(), modelMatrix)) {
            culledThisFrame++;
            return;
        }
        long depthBits = viewDepthBits(modelMatrix);
        for (int i = 0; i < mesh.submeshes().size(); i++) {
            UploadedSubmesh submesh = mesh.submeshes().get(i);
            PerSubmesh perSubmesh = perSubmeshes.get(i);
            writeObjectUbo(perSubmesh.modelUbo(), modelMatrix);
            materialCache.writeMaterialUboIfNeeded(perSubmesh.material(), perSubmesh.classResources());
            submitSubmesh(frame, submesh, perSubmesh, depthBits);
        }
    }

    private void submitSubmesh(FrameBuilder frame, UploadedSubmesh submesh, PerSubmesh perSubmesh, long depthBits) {
        PipelineHandle pipeline = perSubmesh.classResources().pipeline();
        if (perSubmesh.material().transparent()) {
            long backToFrontKey = 0xFFFFFFFFL - depthBits;
            frame.submit(Stage.TRANSPARENT_3D, new DrawCommand(pipeline, submesh.handle(), perSubmesh.litBindings(), backToFrontKey, 1));
            return;
        }
        if (shadowCascades.cascadesActive()) {
            PipelineHandle shadowPipeline = perSubmesh.shadowMasked()
                    ? shadowCascades.maskedPipeline()
                    : shadowCascades.pipeline();
            shadowCascades.submitCaster(new DrawCommand(shadowPipeline, submesh.handle(), perSubmesh.shadowBindings(), 0L, 1));
        }
        long opaqueKey = (pipeline.id() << 32) | depthBits;
        frame.submit(Stage.OPAQUE_3D, new DrawCommand(pipeline, submesh.handle(), perSubmesh.litBindings(), opaqueKey, 1));
    }

    private long viewDepthBits(Matrix4f modelMatrix) {
        float dx = modelMatrix.m30() - scratchCameraPosition.x;
        float dy = modelMatrix.m31() - scratchCameraPosition.y;
        float dz = modelMatrix.m32() - scratchCameraPosition.z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        return Float.floatToIntBits(distanceSquared) & 0xFFFFFFFFL;
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
            iterator.remove();
        }
    }

    private List<PerSubmesh> resolvePerSubmeshes(MeshRenderer renderer, UploadedMesh mesh) {
        RenderableMesh cached = objectResources.get(renderer);
        if (cached != null && cached.mesh() == mesh) {
            return cached.submeshes();
        }
        if (cached != null) {
            destroyPerSubmeshes(cached.submeshes());
        }
        RenderableMesh rebuilt = new RenderableMesh(mesh, createPerSubmeshes(renderer));
        objectResources.put(renderer, rebuilt);
        return rebuilt.submeshes();
    }

    private void destroyPerSubmeshes(List<PerSubmesh> perSubmeshes) {
        for (PerSubmesh perSubmesh : perSubmeshes) {
            backend.destroy(perSubmesh.litBindings());
            backend.destroy(perSubmesh.shadowBindings());
            backend.destroy(perSubmesh.modelUbo());
            ownedBindings.remove(perSubmesh.litBindings());
            ownedBindings.remove(perSubmesh.shadowBindings());
            ownedBuffers.remove(perSubmesh.modelUbo());
        }
    }

    private Material resolveMaterial(MeshRenderer renderer, int slot) {
        return renderer.materialForSlot(slot)
                .or(() -> renderer.materialForSlot(0))
                .orElseGet(this::fallbackMaterial);
    }

    private Material fallbackMaterial() {
        if (fallback == null) {
            fallback = new LitMaterial();
        }
        return fallback;
    }

    private List<PerSubmesh> createPerSubmeshes(MeshRenderer renderer) {
        UploadedMesh mesh = renderer.mesh().orElseThrow(() ->
                new EpysiaException("createPerSubmeshes called on renderer with no mesh"));
        List<PerSubmesh> result = new ArrayList<>(mesh.submeshes().size());
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            result.add(createPerSubmesh(renderer, submesh));
        }
        return result;
    }

    private PerSubmesh createPerSubmesh(MeshRenderer renderer, UploadedSubmesh submesh) {
        Material material = resolveMaterial(renderer, submesh.materialSlot());
        MaterialClassResources classResources = materialCache.classResourcesFor(material);
        BufferHandle materialUbo = materialCache.ensureMaterialUbo(material, classResources);
        ByteBuffer empty = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
        BufferHandle modelUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, empty));
        ownedBuffers.add(modelUbo);
        boolean shadowMasked = shadowMasked(material, materialUbo);
        BindingSetHandle shadowBindings = createShadowBindings(material, classResources, modelUbo, materialUbo, shadowMasked);
        BindingSetHandle litBindings = backend.createBindingSet(buildLitBindingSetDescriptor(material, classResources, modelUbo, materialUbo));
        ownedBindings.add(shadowBindings);
        ownedBindings.add(litBindings);
        return new PerSubmesh(modelUbo, shadowBindings, litBindings, classResources, material,
                captureTextures(material, classResources), shadowMasked);
    }

    private static boolean shadowMasked(Material material, BufferHandle materialUbo) {
        return materialUbo != null && material instanceof LitMaterial lit && lit.alphaCutoff > 0.0f;
    }

    private BindingSetHandle createShadowBindings(Material material, MaterialClassResources classResources,
                                                  BufferHandle modelUbo, BufferHandle materialUbo, boolean masked) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                UniformBufferBinding.whole(frameUboWriter.handle(), MeshShaderBindings.FRAME_UBO_SIZE)));
        bindings.add(new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                UniformBufferBinding.whole(modelUbo, MeshShaderBindings.OBJECT_UBO_SIZE)));
        bindings.add(new Binding(MeshShaderBindings.CASCADE_UBO_BINDING,
                UniformBufferBinding.whole(shadowCascades.cascadeUbo(), MeshShaderBindings.CASCADE_UBO_SIZE)));
        if (!masked) {
            return backend.createBindingSet(new BindingSetDescriptor(shadowCascades.bindingLayout(), bindings));
        }
        bindings.add(new Binding(MeshShaderBindings.SHADOW_MASK_MATERIAL_UBO_BINDING,
                UniformBufferBinding.whole(materialUbo, classResources.metadata().uniformBufferSize())));
        bindings.add(new Binding(MeshShaderBindings.SHADOW_MASK_ALBEDO_BINDING,
                new SampledTextureBinding(shadowAlbedoTexture(material, classResources))));
        return backend.createBindingSet(new BindingSetDescriptor(shadowCascades.maskedBindingLayout(), bindings));
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

    private void refreshStaleTextureBindings(List<PerSubmesh> perSubmeshes) {
        for (int i = 0; i < perSubmeshes.size(); i++) {
            PerSubmesh existing = perSubmeshes.get(i);
            boolean masked = shadowMasked(existing.material(), materialCache.materialUboFor(existing.material()));
            if (!texturesChangedSinceCapture(existing) && masked == existing.shadowMasked()) {
                continue;
            }
            perSubmeshes.set(i, rebuildBindings(existing, masked));
        }
    }

    private PerSubmesh rebuildBindings(PerSubmesh existing, boolean masked) {
        BufferHandle materialUbo = materialCache.materialUboFor(existing.material());
        BindingSetHandle freshLitBindings = backend.createBindingSet(
                buildLitBindingSetDescriptor(existing.material(), existing.classResources(), existing.modelUbo(), materialUbo));
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
                captureTextures(existing.material(), existing.classResources()), masked);
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
                                                              BufferHandle modelUbo, BufferHandle materialUbo) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                UniformBufferBinding.whole(frameUboWriter.handle(), MeshShaderBindings.FRAME_UBO_SIZE)));
        bindings.add(new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                UniformBufferBinding.whole(modelUbo, MeshShaderBindings.OBJECT_UBO_SIZE)));
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
        return new BindingSetDescriptor(classResources.litBindingLayout(), bindings);
    }

    private record PerSubmesh(
            BufferHandle modelUbo,
            BindingSetHandle shadowBindings,
            BindingSetHandle litBindings,
            MaterialClassResources classResources,
            Material material,
            TextureHandle[] capturedTextures,
            boolean shadowMasked
    ) {
    }

    private record RenderableMesh(UploadedMesh mesh, List<PerSubmesh> submeshes) {
    }
}
