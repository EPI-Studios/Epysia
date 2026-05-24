package fr.epistudio.epysia.render.mesh;

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
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.Stage;
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
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialClassMetadata;
import fr.epistudio.epysia.render.material.MaterialClassMetadata.TextureFieldDescriptor;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.texture.Texture2D;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MeshRenderSystem implements RenderSystem {

    private static final String SHADOW_VERTEX_PATH = "shadow.vert.glsl";
    private static final String SHADOW_FRAGMENT_PATH = "shadow.frag.glsl";
    private static final int OBJECT_UBO_SIZE = 64;
    private static final int SHADOW_MAP_SIZE = 1024;
    private static final int VERTEX_STRIDE = MeshData.VERTEX_FLOAT_COUNT * Float.BYTES;
    private static final int FRAME_UBO_BINDING = 0;
    private static final int OBJECT_UBO_BINDING = 1;
    private static final int MATERIAL_UBO_BINDING = 2;
    private static final int SHADOW_MAP_BINDING = 3;
    private static final int MAX_LIGHTS = 8;
    private static final int LIGHT_BYTES = 64;
    private static final int FRAME_HEADER_BYTES = 176;
    private static final int FRAME_UBO_SIZE = FRAME_HEADER_BYTES + MAX_LIGHTS * LIGHT_BYTES;
    private static final int LIGHT_TYPE_DIRECTIONAL = 0;
    private static final int LIGHT_TYPE_POINT = 1;
    private static final int LIGHT_TYPE_SPOT = 2;

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;

    private RenderBackend backend;
    private PipelineHandle shadowPipeline;
    private BufferHandle frameUbo;
    private TextureHandle shadowMap;
    private TextureHandle defaultAlbedo;
    private RenderTargetHandle shadowTarget;
    private BindingSetLayout shadowBindingLayout;

    private final Map<Class<? extends Material>, MaterialClassResources> classCache = new HashMap<>();
    private final Map<Material, BufferHandle> materialUbos = new IdentityHashMap<>();
    private final Map<MeshRenderer, List<PerSubmesh>> objectResources = new IdentityHashMap<>();
    private final Set<Material> materialsWrittenThisFrame = new HashSet<>();
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();
    private final List<BindingSetHandle> ownedBindings = new ArrayList<>();
    private final List<Light> activeLights = new ArrayList<>(16);
    private final ByteBuffer scratchFrameUbo = BufferUtils.createByteBuffer(FRAME_UBO_SIZE);
    private final ByteBuffer scratchObjectUbo = BufferUtils.createByteBuffer(OBJECT_UBO_SIZE);
    private final ByteBuffer scratchMaterialUbo = BufferUtils.createByteBuffer(1024);
    private final Vector3f scratchLightDirection = new Vector3f();
    private final Vector3f scratchLightPosition = new Vector3f();
    private final Vector3f scratchCameraPosition = new Vector3f();
    private final org.joml.FrustumIntersection frustum = new org.joml.FrustumIntersection();
    private final org.joml.Vector3f scratchWorldMin = new org.joml.Vector3f();
    private final org.joml.Vector3f scratchWorldMax = new org.joml.Vector3f();
    private final org.joml.Vector3f scratchCorner = new org.joml.Vector3f();
    private TextureHandle defaultNormalMap;
    private int culledThisFrame;

    private final fr.epistudio.epysia.render.backend.RenderSurface window;

    public MeshRenderSystem(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger, fr.epistudio.epysia.render.backend.RenderSurface window) {
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.logger = logger;
        this.window = window;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        buildShadowBindingLayout();
        createShadowResources(configurer);
        defaultAlbedo = Texture2D.whitePixel(backend);
        defaultNormalMap = createFlatNormalTexture(backend);
        createFrameUbo();
        shadowPipeline = backend.createPipeline(buildShadowPipelineDescriptor());
        registerShadowReload();
    }

    private void buildShadowBindingLayout() {
        shadowBindingLayout = new BindingSetLayout(List.of(
                new BindingSlot(FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER)
        ));
    }

    private void createShadowResources(StageConfigurer configurer) {
        shadowMap = backend.createTexture(new TextureDescriptor(
                SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_SHADOW
        ));
        shadowTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, List.of(), Optional.of(shadowMap)
        ));
        configurer.bindStageTarget(Stage.PRE_3D, shadowTarget, PassClear.depthOnly());
    }

    private void createFrameUbo() {
        ByteBuffer initial = BufferUtils.createByteBuffer(FRAME_UBO_SIZE);
        frameUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, initial));
    }

    private static TextureHandle createFlatNormalTexture(RenderBackend backend) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0x80).put((byte) 0x80).put((byte) 0xFF).put((byte) 0xFF).flip();
        TextureHandle handle = backend.createTexture(new TextureDescriptor(1, 1, TextureFormat.RGBA8, TextureUsage.SAMPLED));
        backend.writeTexture(handle, pixel);
        return handle;
    }

    private PipelineDescriptor buildShadowPipelineDescriptor() {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexLayout layout = new VertexLayout(List.of(position), VERTEX_STRIDE);
        return new PipelineDescriptor(
                loadShaderSource(SHADOW_VERTEX_PATH, SHADOW_FRAGMENT_PATH),
                layout,
                RenderState.OPAQUE_3D,
                shadowBindingLayout
        );
    }

    private void registerShadowReload() {
        if (!shaderWatcher.active()) {
            return;
        }
        Set<String> dependencies = collectDependencies(SHADOW_VERTEX_PATH, SHADOW_FRAGMENT_PATH);
        shaderWatcher.watch(List.copyOf(dependencies), () -> reloadPipeline(shadowPipeline, SHADOW_VERTEX_PATH, SHADOW_FRAGMENT_PATH));
    }

    private Set<String> collectDependencies(String vertexPath, String fragmentPath) {
        Set<String> dependencies = new LinkedHashSet<>();
        dependencies.addAll(shaderLoader.load(vertexPath).dependencyPaths());
        dependencies.addAll(shaderLoader.load(fragmentPath).dependencyPaths());
        return dependencies;
    }

    private void reloadPipeline(PipelineHandle pipeline, String vertexPath, String fragmentPath) {
        try {
            backend.updatePipelineShaders(pipeline, loadShaderSource(vertexPath, fragmentPath));
            logger.info("Reloaded shader pipeline: " + vertexPath + " + " + fragmentPath);
        } catch (EpysiaException exception) {
            logger.error("Shader reload failed for " + vertexPath + " + " + fragmentPath + " — keeping previous program", exception);
        }
    }

    private ShaderSource loadShaderSource(String vertexPath, String fragmentPath) {
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, float interpolationAlpha) {
        shaderWatcher.poll();
        Camera3D camera = findComponent(scene, Camera3D.class);
        DirectionalLight primaryDirectional = findComponent(scene, DirectionalLight.class);
        if (camera == null || primaryDirectional == null) {
            return;
        }
        int viewportHeight = Math.max(1, window.framebufferHeight());
        camera.setAspectRatio((float) window.framebufferWidth() / (float) viewportHeight);
        List<Light> lights = gatherLights(scene, primaryDirectional);
        writeFrameUbo(camera, primaryDirectional, lights);
        materialsWrittenThisFrame.clear();
        frustum.set(camera.viewProjection());
        culledThisFrame = 0;
        for (GameObject gameObject : scene.gameObjects()) {
            submitMeshDraws(gameObject, frame);
        }
    }

    private List<Light> gatherLights(Scene scene, DirectionalLight primary) {
        activeLights.clear();
        activeLights.add(primary);
        for (GameObject gameObject : scene.gameObjects()) {
            Light light = gameObject.getComponent(Light.class).orElse(null);
            if (light != null && light != primary && activeLights.size() < MAX_LIGHTS) {
                activeLights.add(light);
            }
        }
        return activeLights;
    }

    private <T extends IComponent> T findComponent(Scene scene, Class<T> componentClass) {
        for (GameObject gameObject : scene.gameObjects()) {
            Optional<T> component = gameObject.getComponent(componentClass);
            if (component.isPresent()) {
                return component.get();
            }
        }
        return null;
    }

    private void writeFrameUbo(Camera3D camera, DirectionalLight primary, List<Light> lights) {
        Matrix4f cameraViewProjection = camera.viewProjection();
        Matrix4f lightViewProjection = primary.viewProjection();
        scratchFrameUbo.clear();
        cameraViewProjection.get(0, scratchFrameUbo);
        lightViewProjection.get(64, scratchFrameUbo);
        Vector3f ambient = primary.ambient();
        scratchFrameUbo.position(128);
        scratchFrameUbo.putFloat(ambient.x).putFloat(ambient.y).putFloat(ambient.z).putFloat(0.0f);
        camera.position(scratchCameraPosition);
        scratchFrameUbo.putFloat(scratchCameraPosition.x).putFloat(scratchCameraPosition.y).putFloat(scratchCameraPosition.z).putFloat(0.0f);
        scratchFrameUbo.putInt(lights.size()).putInt(0).putInt(0).putInt(0);
        for (int i = 0; i < lights.size(); i++) {
            writeLight(lights.get(i));
        }
        for (int i = lights.size(); i < MAX_LIGHTS; i++) {
            writeBlankLight();
        }
        scratchFrameUbo.flip();
        backend.writeBuffer(frameUbo, scratchFrameUbo, 0L);
    }

    private void writeLight(Light light) {
        if (light instanceof DirectionalLight directional) {
            directional.direction(scratchLightDirection);
            scratchFrameUbo.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(LIGHT_TYPE_DIRECTIONAL);
            scratchFrameUbo.putFloat(scratchLightDirection.x).putFloat(scratchLightDirection.y).putFloat(scratchLightDirection.z).putFloat(0.0f);
        } else if (light instanceof PointLight point) {
            point.position(scratchLightPosition);
            scratchFrameUbo.putFloat(scratchLightPosition.x).putFloat(scratchLightPosition.y).putFloat(scratchLightPosition.z).putFloat(LIGHT_TYPE_POINT);
            scratchFrameUbo.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(point.range());
        } else if (light instanceof SpotLight spot) {
            spot.position(scratchLightPosition);
            spot.direction(scratchLightDirection);
            scratchFrameUbo.putFloat(scratchLightPosition.x).putFloat(scratchLightPosition.y).putFloat(scratchLightPosition.z).putFloat(LIGHT_TYPE_SPOT);
            scratchFrameUbo.putFloat(scratchLightDirection.x).putFloat(scratchLightDirection.y).putFloat(scratchLightDirection.z).putFloat(spot.range());
        } else {
            writeBlankLight();
            return;
        }
        Vector3f color = light.color();
        scratchFrameUbo.putFloat(color.x).putFloat(color.y).putFloat(color.z).putFloat(light.intensity());
        if (light instanceof SpotLight spot) {
            scratchFrameUbo.putFloat(spot.innerConeCosine()).putFloat(spot.outerConeCosine()).putFloat(0.0f).putFloat(0.0f);
        } else {
            scratchFrameUbo.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        }
    }

    private void writeBlankLight() {
        for (int i = 0; i < LIGHT_BYTES / Float.BYTES; i++) {
            scratchFrameUbo.putFloat(0.0f);
        }
    }

    private void submitMeshDraws(GameObject gameObject, FrameBuilder frame) {
        Optional<MeshRenderer> rendererOpt = gameObject.getComponent(MeshRenderer.class);
        Optional<Transform3D> transformOpt = gameObject.getComponent(Transform3D.class);
        if (rendererOpt.isEmpty() || transformOpt.isEmpty()) {
            return;
        }
        MeshRenderer renderer = rendererOpt.get();
        UploadedMesh mesh = renderer.mesh();
        if (mesh == null) {
            return;
        }
        List<PerSubmesh> perSubmeshes = objectResources.computeIfAbsent(renderer, ignored -> createPerSubmeshes(renderer));
        refreshStaleTextureBindings(perSubmeshes);
        Matrix4f modelMatrix = transformOpt.get().localMatrix();
        if (isCulled(mesh, modelMatrix)) {
            culledThisFrame++;
            return;
        }
        for (int i = 0; i < mesh.submeshes().size(); i++) {
            UploadedSubmesh submesh = mesh.submeshes().get(i);
            PerSubmesh perSubmesh = perSubmeshes.get(i);
            writeObjectUbo(perSubmesh.modelUbo(), modelMatrix);
            ensureMaterialUboWritten(perSubmesh.material(), perSubmesh.classResources());
            frame.submit(Stage.PRE_3D, new DrawCommand(shadowPipeline, submesh.handle(), perSubmesh.shadowBindings(), 0L, 1));
            frame.submit(Stage.OPAQUE_3D, new DrawCommand(perSubmesh.classResources().pipeline(), submesh.handle(), perSubmesh.litBindings(), 0L, 1));
        }
    }

    private boolean isCulled(UploadedMesh mesh, Matrix4f modelMatrix) {
        Aabb local = mesh.localBounds();
        if (local == null) {
            return false;
        }
        computeWorldAabb(local, modelMatrix);
        return !frustum.testAab(scratchWorldMin, scratchWorldMax);
    }

    private void computeWorldAabb(Aabb local, Matrix4f modelMatrix) {
        scratchWorldMin.set(Float.POSITIVE_INFINITY);
        scratchWorldMax.set(Float.NEGATIVE_INFINITY);
        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? local.minX() : local.maxX();
            float y = (corner & 2) == 0 ? local.minY() : local.maxY();
            float z = (corner & 4) == 0 ? local.minZ() : local.maxZ();
            scratchCorner.set(x, y, z);
            modelMatrix.transformPosition(scratchCorner);
            scratchWorldMin.min(scratchCorner);
            scratchWorldMax.max(scratchCorner);
        }
    }

    public int culledMeshCount() {
        return culledThisFrame;
    }

    private void ensureMaterialUboWritten(Material material, MaterialClassResources classResources) {
        if (material == null || !classResources.metadata().hasUniformBuffer()) {
            return;
        }
        if (!materialsWrittenThisFrame.add(material)) {
            return;
        }
        BufferHandle materialUbo = materialUbos.get(material);
        if (materialUbo == null) {
            return;
        }
        scratchMaterialUbo.clear();
        scratchMaterialUbo.limit(classResources.metadata().uniformBufferSize());
        for (int i = 0; i < classResources.metadata().uniformBufferSize(); i++) {
            scratchMaterialUbo.put(i, (byte) 0);
        }
        classResources.metadata().writeUniformBuffer(material, scratchMaterialUbo);
        scratchMaterialUbo.position(0);
        scratchMaterialUbo.limit(classResources.metadata().uniformBufferSize());
        backend.writeBuffer(materialUbo, scratchMaterialUbo, 0L);
    }

    private List<PerSubmesh> createPerSubmeshes(MeshRenderer renderer) {
        UploadedMesh mesh = renderer.mesh();
        List<PerSubmesh> result = new ArrayList<>(mesh.submeshes().size());
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            result.add(createPerSubmesh(renderer, submesh));
        }
        return result;
    }

    private PerSubmesh createPerSubmesh(MeshRenderer renderer, UploadedSubmesh submesh) {
        Material material = renderer.materialForSlot(submesh.materialSlot())
                .orElseThrow(() -> new EpysiaException("MeshRenderer has no Material at slot " + submesh.materialSlot()));
        MaterialClassResources classResources = classResourcesFor(material);
        BufferHandle materialUbo = ensureMaterialUbo(material, classResources);
        ByteBuffer empty = BufferUtils.createByteBuffer(OBJECT_UBO_SIZE);
        BufferHandle modelUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, empty));
        ownedBuffers.add(modelUbo);
        BindingSetHandle shadowBindings = backend.createBindingSet(new BindingSetDescriptor(
                shadowBindingLayout,
                List.of(
                        new Binding(FRAME_UBO_BINDING, UniformBufferBinding.whole(frameUbo, FRAME_UBO_SIZE)),
                        new Binding(OBJECT_UBO_BINDING, UniformBufferBinding.whole(modelUbo, OBJECT_UBO_SIZE))
                )
        ));
        BindingSetHandle litBindings = backend.createBindingSet(buildLitBindingSetDescriptor(material, classResources, modelUbo, materialUbo));
        ownedBindings.add(shadowBindings);
        ownedBindings.add(litBindings);
        return new PerSubmesh(modelUbo, shadowBindings, litBindings, classResources, material, captureTextures(material, classResources));
    }

    private TextureHandle[] captureTextures(Material material, MaterialClassResources classResources) {
        List<TextureFieldDescriptor> textureFields = classResources.metadata().textureFields();
        TextureHandle[] snapshot = new TextureHandle[textureFields.size()];
        for (int i = 0; i < textureFields.size(); i++) {
            TextureHandle current = classResources.metadata().readTexture(material, textureFields.get(i));
            snapshot[i] = current != null ? current : defaultFor(textureFields.get(i));
        }
        return snapshot;
    }

    private void refreshStaleTextureBindings(List<PerSubmesh> perSubmeshes) {
        for (int i = 0; i < perSubmeshes.size(); i++) {
            PerSubmesh existing = perSubmeshes.get(i);
            if (!texturesChangedSinceCapture(existing)) {
                continue;
            }
            BufferHandle materialUbo = materialUbos.get(existing.material());
            BindingSetHandle freshLitBindings = backend.createBindingSet(
                    buildLitBindingSetDescriptor(existing.material(), existing.classResources(), existing.modelUbo(), materialUbo));
            backend.destroy(existing.litBindings());
            ownedBindings.remove(existing.litBindings());
            ownedBindings.add(freshLitBindings);
            perSubmeshes.set(i, new PerSubmesh(
                    existing.modelUbo(),
                    existing.shadowBindings(),
                    freshLitBindings,
                    existing.classResources(),
                    existing.material(),
                    captureTextures(existing.material(), existing.classResources())
            ));
        }
    }

    private boolean texturesChangedSinceCapture(PerSubmesh perSubmesh) {
        List<TextureFieldDescriptor> textureFields = perSubmesh.classResources().metadata().textureFields();
        TextureHandle[] captured = perSubmesh.capturedTextures();
        for (int i = 0; i < textureFields.size(); i++) {
            TextureHandle current = perSubmesh.classResources().metadata().readTexture(perSubmesh.material(), textureFields.get(i));
            if (current == null) {
                current = defaultFor(textureFields.get(i));
            }
            if (!current.equals(captured[i])) {
                return true;
            }
        }
        return false;
    }

    private BindingSetDescriptor buildLitBindingSetDescriptor(Material material, MaterialClassResources classResources, BufferHandle modelUbo, BufferHandle materialUbo) {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(FRAME_UBO_BINDING, UniformBufferBinding.whole(frameUbo, FRAME_UBO_SIZE)));
        bindings.add(new Binding(OBJECT_UBO_BINDING, UniformBufferBinding.whole(modelUbo, OBJECT_UBO_SIZE)));
        if (classResources.metadata().hasUniformBuffer()) {
            bindings.add(new Binding(MATERIAL_UBO_BINDING, UniformBufferBinding.whole(materialUbo, classResources.metadata().uniformBufferSize())));
        }
        bindings.add(new Binding(SHADOW_MAP_BINDING, new SampledTextureBinding(shadowMap)));
        for (TextureFieldDescriptor textureField : classResources.metadata().textureFields()) {
            TextureHandle texture = classResources.metadata().readTexture(material, textureField);
            TextureHandle fallback = defaultFor(textureField);
            bindings.add(new Binding(textureField.slotIndex(), new SampledTextureBinding(texture != null ? texture : fallback)));
        }
        return new BindingSetDescriptor(classResources.litBindingLayout(), bindings);
    }

    private TextureHandle defaultFor(TextureFieldDescriptor field) {
        String name = field.reflectField().getName().toLowerCase();
        if (name.contains("normal") || name.contains("bump")) {
            return defaultNormalMap;
        }
        return defaultAlbedo;
    }

    private MaterialClassResources classResourcesFor(Material material) {
        return classCache.computeIfAbsent(material.getClass(), ignored -> buildMaterialClassResources(material));
    }

    private MaterialClassResources buildMaterialClassResources(Material material) {
        LoadedShader fragmentLoaded = shaderLoader.load(material.fragmentShaderPath());
        MaterialClassMetadata metadata = MaterialClassMetadata.reflect(material.getClass(), fragmentLoaded.source());
        BindingSetLayout litLayout = buildLitBindingLayout(metadata);
        PipelineHandle pipeline = backend.createPipeline(buildLitPipelineDescriptor(material, litLayout));
        registerMaterialReload(pipeline, material.vertexShaderPath(), material.fragmentShaderPath());
        return new MaterialClassResources(metadata, pipeline, litLayout);
    }

    private BindingSetLayout buildLitBindingLayout(MaterialClassMetadata metadata) {
        List<BindingSlot> slots = new ArrayList<>();
        slots.add(new BindingSlot(FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        slots.add(new BindingSlot(OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        if (metadata.hasUniformBuffer()) {
            slots.add(new BindingSlot(MATERIAL_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        }
        slots.add(new BindingSlot(SHADOW_MAP_BINDING, BindingType.SAMPLED_TEXTURE_2D));
        for (TextureFieldDescriptor textureField : metadata.textureFields()) {
            slots.add(new BindingSlot(textureField.slotIndex(), BindingType.SAMPLED_TEXTURE_2D));
        }
        return new BindingSetLayout(slots);
    }

    private PipelineDescriptor buildLitPipelineDescriptor(Material material, BindingSetLayout bindingLayout) {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexAttribute normal = new VertexAttribute(1, VertexFormat.FLOAT3, 12);
        VertexAttribute uv = new VertexAttribute(2, VertexFormat.FLOAT2, 24);
        VertexAttribute tangent = new VertexAttribute(3, VertexFormat.FLOAT3, 32);
        VertexLayout layout = new VertexLayout(List.of(position, normal, uv, tangent), VERTEX_STRIDE);
        return new PipelineDescriptor(
                loadShaderSource(material.vertexShaderPath(), material.fragmentShaderPath()),
                layout,
                RenderState.OPAQUE_3D,
                bindingLayout
        );
    }

    private void registerMaterialReload(PipelineHandle pipeline, String vertexPath, String fragmentPath) {
        if (!shaderWatcher.active()) {
            return;
        }
        Set<String> dependencies = collectDependencies(vertexPath, fragmentPath);
        shaderWatcher.watch(List.copyOf(dependencies), () -> reloadPipeline(pipeline, vertexPath, fragmentPath));
    }

    private BufferHandle ensureMaterialUbo(Material material, MaterialClassResources classResources) {
        if (!classResources.metadata().hasUniformBuffer()) {
            return null;
        }
        BufferHandle existing = materialUbos.get(material);
        if (existing != null) {
            return existing;
        }
        ByteBuffer empty = BufferUtils.createByteBuffer(classResources.metadata().uniformBufferSize());
        BufferHandle ubo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, empty));
        ownedBuffers.add(ubo);
        materialUbos.put(material, ubo);
        return ubo;
    }

    private void writeObjectUbo(BufferHandle ubo, Matrix4f model) {
        scratchObjectUbo.clear();
        model.get(0, scratchObjectUbo);
        scratchObjectUbo.position(0);
        scratchObjectUbo.limit(OBJECT_UBO_SIZE);
        backend.writeBuffer(ubo, scratchObjectUbo, 0L);
    }

    @Override
    public void shutdown(RenderBackend backend) {
        for (BindingSetHandle binding : ownedBindings) {
            backend.destroy(binding);
        }
        for (BufferHandle buffer : ownedBuffers) {
            backend.destroy(buffer);
        }
        for (MaterialClassResources resources : classCache.values()) {
            backend.destroy(resources.pipeline());
        }
        backend.destroy(frameUbo);
        backend.destroy(shadowPipeline);
        backend.destroy(shadowTarget);
        backend.destroy(shadowMap);
        backend.destroy(defaultAlbedo);
        backend.destroy(defaultNormalMap);
        objectResources.clear();
        materialUbos.clear();
        classCache.clear();
        ownedBindings.clear();
        ownedBuffers.clear();
    }

    private record MaterialClassResources(MaterialClassMetadata metadata, PipelineHandle pipeline, BindingSetLayout litBindingLayout) {
    }

    private record PerSubmesh(
            BufferHandle modelUbo,
            BindingSetHandle shadowBindings,
            BindingSetHandle litBindings,
            MaterialClassResources classResources,
            Material material,
            TextureHandle[] capturedTextures
    ) {
    }
}
