package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialClassMetadata;
import fr.epistudio.epysia.render.material.MaterialClassMetadata.TextureFieldDescriptor;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.shader.SurfaceShaderComposer;
import fr.epistudio.epysia.render.texture.Texture2D;
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

final class MaterialPipelineCache {

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;
    private final ByteBuffer scratchMaterialUbo = BufferUtils.createByteBuffer(1024);
    private final Map<String, MaterialClassResources> classCache = new HashMap<>();
    private final Map<Material, ResolvedClass> resolvedClasses = new IdentityHashMap<>();
    private static final byte[] NO_UNIFORM_BYTES = new byte[0];

    private final Map<Material, BufferHandle> materialUbos = new IdentityHashMap<>();
    private final Map<Material, byte[]> uniformSnapshots = new IdentityHashMap<>();
    private final Set<Material> writtenThisFrame = new HashSet<>();
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();

    private RenderBackend backend;
    private TextureHandle defaultAlbedo;
    private TextureHandle defaultNormalMap;

    MaterialPipelineCache(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.logger = logger;
    }

    void initialize(RenderBackend backend) {
        this.backend = backend;
        defaultAlbedo = Texture2D.whitePixel(backend);
        defaultNormalMap = createFlatNormalTexture(backend);
    }

    void beginFrame() {
        writtenThisFrame.clear();
    }

    private record ResolvedClass(String vertexShader, String fragmentShader, String surfaceShader,
                                 boolean transparent, boolean doubleSided, MaterialClassResources resources) {

        boolean matches(Material material) {
            return transparent == material.transparent()
                    && doubleSided == material.doubleSided()
                    && vertexShader.equals(material.vertexShaderPath())
                    && fragmentShader.equals(material.fragmentShaderPath())
                    && surfaceShader.equals(surfaceShaderPathOf(material));
        }
    }

    MaterialClassResources classResourcesFor(Material material) {
        ResolvedClass resolved = resolvedClasses.get(material);
        if (resolved != null && resolved.matches(material)) {
            return resolved.resources();
        }
        MaterialClassResources built = classCache.computeIfAbsent(pipelineKey(material),
                ignored -> buildOrFallback(material));
        resolvedClasses.put(material, new ResolvedClass(material.vertexShaderPath(),
                material.fragmentShaderPath(), surfaceShaderPathOf(material),
                material.transparent(), material.doubleSided(), built));
        return built;
    }

    ParsedSource surfaceUniformsFor(Material material) {
        return classResourcesFor(material).surfaceUniforms();
    }

    private MaterialClassResources buildOrFallback(Material material) {
        try {
            return buildClassResources(material);
        } catch (EpysiaException failure) {
            String surfacePath = surfaceShaderPathOf(material);
            if (surfacePath.isEmpty()) {
                throw failure;
            }
            logger.error("Surface shader '" + surfacePath
                    + "' failed to compile, falling back to the base material shaders", failure);
            return buildFallbackClassResources(material, surfacePath);
        }
    }

    private MaterialClassResources buildFallbackClassResources(Material material, String surfacePath) {
        LoadedPrograms programs = loadPrograms(material.vertexShaderPath(), material.fragmentShaderPath(), "");
        MaterialClassMetadata metadata =
                MaterialClassMetadata.reflect(material.getClass(), programs.fragment().source());
        BindingSetLayout litLayout = buildLitBindingLayout(metadata, ParsedSource.empty());
        PipelineHandle pipeline = backend.createPipeline(buildLitPipelineDescriptor(material, programs, litLayout));
        registerHotReload(pipelineKey(material), pipeline, material.vertexShaderPath(),
                material.fragmentShaderPath(), surfacePath, programs);
        return new MaterialClassResources(metadata, pipeline, litLayout, ParsedSource.empty(),
                supportsInstancing(material, ""));
    }

    private static String pipelineKey(Material material) {
        return material.getClass().getName() + "|" + material.vertexShaderPath() + "|" + material.fragmentShaderPath()
                + "|" + surfaceShaderPathOf(material)
                + "|" + (material.transparent() ? "transparent" : "opaque")
                + "|" + (material.doubleSided() ? "doubleSided" : "culled");
    }

    static String surfaceShaderPathOf(Material material) {
        return material instanceof LitMaterial lit ? lit.surfaceShaderPath() : "";
    }

    BufferHandle ensureMaterialUbo(Material material, MaterialClassResources classResources) {
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

    BufferHandle materialUboFor(Material material) {
        return materialUbos.get(material);
    }

    void writeMaterialUboIfNeeded(Material material, MaterialClassResources classResources) {
        if (material == null || !classResources.metadata().hasUniformBuffer()) {
            return;
        }
        if (!writtenThisFrame.add(material)) {
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
        captureUniformSnapshot(material, classResources.metadata().uniformBufferSize());
        backend.writeBuffer(materialUbo, scratchMaterialUbo, 0L);
    }

    private void captureUniformSnapshot(Material material, int size) {
        byte[] snapshot = uniformSnapshots.get(material);
        if (snapshot == null || snapshot.length != size) {
            snapshot = new byte[size];
            uniformSnapshots.put(material, snapshot);
        }
        for (int index = 0; index < size; index++) {
            snapshot[index] = scratchMaterialUbo.get(index);
        }
    }

    byte[] uniformSnapshotOf(Material material) {
        byte[] snapshot = uniformSnapshots.get(material);
        return snapshot != null ? snapshot : NO_UNIFORM_BYTES;
    }

    TextureHandle defaultFor(TextureFieldDescriptor field) {
        String name = field.reflectField().getName().toLowerCase();
        if (name.contains("normal") || name.contains("bump")) {
            return defaultNormalMap;
        }
        return defaultAlbedo;
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        for (BufferHandle buffer : ownedBuffers) {
            backend.destroy(buffer);
        }
        for (MaterialClassResources resources : classCache.values()) {
            backend.destroy(resources.pipeline());
        }
        if (defaultAlbedo != null) backend.destroy(defaultAlbedo);
        if (defaultNormalMap != null) backend.destroy(defaultNormalMap);
        ownedBuffers.clear();
        materialUbos.clear();
        classCache.clear();
        resolvedClasses.clear();
        writtenThisFrame.clear();
    }

    private MaterialClassResources buildClassResources(Material material) {
        LoadedPrograms programs = loadPrograms(material);
        MaterialClassMetadata metadata = MaterialClassMetadata.reflect(material.getClass(), programs.fragment().source());
        ParsedSource surfaceUniforms = parseSurfaceUniforms(surfaceShaderPathOf(material));
        BindingSetLayout litLayout = buildLitBindingLayout(metadata, surfaceUniforms);
        PipelineHandle pipeline = backend.createPipeline(buildLitPipelineDescriptor(material, programs, litLayout));
        registerHotReload(pipelineKey(material), pipeline, material.vertexShaderPath(),
                material.fragmentShaderPath(), surfaceShaderPathOf(material), programs);
        return new MaterialClassResources(metadata, pipeline, litLayout, surfaceUniforms,
                supportsInstancing(material, surfaceShaderPathOf(material)));
    }

    private boolean supportsInstancing(Material material, String surfacePath) {
        if (material.transparent()) {
            return false;
        }
        return surfacePath.isEmpty() || !SurfaceShaderComposer.usesObjectHelpers(shaderLoader.load(surfacePath));
    }

    private ParsedSource parseSurfaceUniforms(String surfacePath) {
        return surfacePath.isEmpty()
                ? ParsedSource.empty()
                : SurfaceShaderComposer.parseUniforms(shaderLoader.load(surfacePath));
    }

    private LoadedPrograms loadPrograms(Material material) {
        return loadPrograms(material.vertexShaderPath(), material.fragmentShaderPath(), surfaceShaderPathOf(material));
    }

    private LoadedPrograms loadPrograms(String vertexPath, String fragmentPath, String surfacePath) {
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        if (surfacePath.isEmpty()) {
            return new LoadedPrograms(vertex, fragment);
        }
        LoadedShader surface = shaderLoader.load(surfacePath);
        return new LoadedPrograms(
                SurfaceShaderComposer.composeVertex(vertex, surface),
                SurfaceShaderComposer.composeFragment(fragment, surface));
    }

    private BindingSetLayout buildLitBindingLayout(MaterialClassMetadata metadata, ParsedSource surfaceUniforms) {
        List<BindingSlot> slots = new ArrayList<>();
        slots.add(new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.LIGHT_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.CLUSTER_COUNT_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.CLUSTER_INDEX_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.INSTANCE_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        if (metadata.hasUniformBuffer()) {
            slots.add(new BindingSlot(MeshShaderBindings.MATERIAL_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        }
        slots.add(new BindingSlot(MeshShaderBindings.SHADOW_MAP_BINDING, BindingType.SAMPLED_TEXTURE_ARRAY));
        for (TextureFieldDescriptor textureField : metadata.textureFields()) {
            slots.add(new BindingSlot(textureField.slotIndex(), BindingType.SAMPLED_TEXTURE_2D));
        }
        slots.add(new BindingSlot(MeshShaderBindings.IRRADIANCE_MAP_BINDING, BindingType.SAMPLED_TEXTURE_CUBE));
        slots.add(new BindingSlot(MeshShaderBindings.PREFILTERED_MAP_BINDING, BindingType.SAMPLED_TEXTURE_CUBE));
        slots.add(new BindingSlot(MeshShaderBindings.BRDF_LUT_BINDING, BindingType.SAMPLED_TEXTURE_2D));
        slots.add(new BindingSlot(MeshShaderBindings.SPOT_SHADOW_ATLAS_BINDING, BindingType.SAMPLED_TEXTURE_ARRAY));
        slots.add(new BindingSlot(MeshShaderBindings.POINT_SHADOW_ATLAS_BINDING, BindingType.SAMPLED_TEXTURE_ARRAY));
        SurfaceUniformBinder.appendSlots(slots, surfaceUniforms);
        return new BindingSetLayout(slots);
    }

    private PipelineDescriptor buildLitPipelineDescriptor(Material material, LoadedPrograms programs,
                                                          BindingSetLayout bindingLayout) {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexAttribute normal = new VertexAttribute(1, VertexFormat.FLOAT3, 12);
        VertexAttribute uv = new VertexAttribute(2, VertexFormat.FLOAT2, 24);
        VertexAttribute tangent = new VertexAttribute(3, VertexFormat.FLOAT3, 32);
        VertexLayout layout = new VertexLayout(List.of(position, normal, uv, tangent), MeshShaderBindings.VERTEX_STRIDE);
        RenderState state = renderStateFor(material);
        return new PipelineDescriptor(programs.shaderSource(), layout, state, bindingLayout);
    }

    private static RenderState renderStateFor(Material material) {
        RenderState base = material.transparent() ? RenderState.TRANSPARENT_3D : RenderState.OPAQUE_3D;
        if (!material.doubleSided()) {
            return base;
        }
        return new RenderState(base.topology(), base.depthTest(), base.blendMode(), CullMode.NONE, base.depthWrite());
    }

    private void registerHotReload(String cacheKey, PipelineHandle pipeline, String vertexPath, String fragmentPath,
                                   String surfacePath, LoadedPrograms programs) {
        if (!shaderWatcher.active()) {
            return;
        }
        Set<String> dependencies = new LinkedHashSet<>();
        dependencies.addAll(programs.vertex().dependencyPaths());
        dependencies.addAll(programs.fragment().dependencyPaths());
        if (!surfacePath.isEmpty()) {
            dependencies.add(surfacePath);
        }
        shaderWatcher.watch(List.copyOf(dependencies),
                () -> reloadPipeline(cacheKey, pipeline, vertexPath, fragmentPath, surfacePath));
    }

    private void reloadPipeline(String cacheKey, PipelineHandle pipeline,
                                String vertexPath, String fragmentPath, String surfacePath) {
        try {
            if (surfaceDeclarationsChanged(cacheKey, surfacePath)) {
                evictForRebuild(cacheKey, surfacePath);
                return;
            }
            backend.updatePipelineShaders(pipeline, loadPrograms(vertexPath, fragmentPath, surfacePath).shaderSource());
            logger.info("Reloaded shader pipeline: " + vertexPath + " + " + fragmentPath
                    + (surfacePath.isEmpty() ? "" : " + " + surfacePath));
        } catch (EpysiaException exception) {
            logger.error("Shader reload failed for " + vertexPath + " + " + fragmentPath + ", keeping previous program", exception);
        }
    }

    private boolean surfaceDeclarationsChanged(String cacheKey, String surfacePath) {
        MaterialClassResources cached = classCache.get(cacheKey);
        return cached != null && !cached.surfaceUniforms().declarations()
                .equals(parseSurfaceUniforms(surfacePath).declarations());
    }

    private void evictForRebuild(String cacheKey, String surfacePath) {
        MaterialClassResources removed = classCache.remove(cacheKey);
        if (removed == null) {
            return;
        }
        backend.destroy(removed.pipeline());
        logger.info("Surface shader uniforms changed in " + surfacePath + ", rebuilding material pipeline");
    }

    private record LoadedPrograms(LoadedShader vertex, LoadedShader fragment) {

        ShaderSource shaderSource() {
            return new ShaderSource(vertex.source(), fragment.source());
        }
    }

    private static TextureHandle createFlatNormalTexture(RenderBackend backend) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0x80).put((byte) 0x80).put((byte) 0xFF).put((byte) 0xFF).flip();
        TextureHandle handle = backend.createTexture(new TextureDescriptor(1, 1, TextureFormat.RGBA8, TextureUsage.SAMPLED));
        backend.writeTexture(handle, pixel);
        return handle;
    }
}
