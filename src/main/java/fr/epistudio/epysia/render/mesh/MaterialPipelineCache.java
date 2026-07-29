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
import fr.epistudio.epysia.render.shader.ShaderUniformParser;
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
    private final Map<Class<? extends Material>, MaterialClassMetadata> reflectionCache = new HashMap<>();
    private final Map<Material, ResolvedClass> resolvedClasses = new IdentityHashMap<>();
    private static final byte[] NO_UNIFORM_BYTES = new byte[0];

    private final Map<Material, BufferHandle> materialUbos = new IdentityHashMap<>();
    private final Map<Material, byte[]> uniformSnapshots = new IdentityHashMap<>();
    private final Set<Material> writtenThisFrame = new HashSet<>();
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();

    private RenderBackend backend;
    private TextureHandle defaultAlbedo;
    private TextureHandle defaultNormalMap;
    private boolean probeLightingActive;

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
        frameTexturePresenceMasks.clear();
    }

    private final Map<Material, Long> frameTexturePresenceMasks = new IdentityHashMap<>();

    void setProbeLightingActive(boolean active) {
        this.probeLightingActive = active;
    }

    boolean probeLightingActive() {
        return probeLightingActive;
    }

    private record ResolvedClass(String vertexShader, String fragmentShader, String surfaceShader,
                                 boolean transparent, boolean doubleSided, long textureMask,
                                 MaterialClassResources resources) {

        boolean matches(Material material, long currentTextureMask) {
            return transparent == material.blended()
                    && doubleSided == material.doubleSided()
                    && textureMask == currentTextureMask
                    && vertexShader.equals(material.vertexShaderPath())
                    && fragmentShader.equals(material.fragmentShaderPath())
                    && surfaceShader.equals(surfaceShaderPathOf(material));
        }
    }

    MaterialClassResources classResourcesFor(Material material) {
        return classResourcesFor(material, false, false);
    }

    MaterialClassResources classResourcesFor(Material material, boolean skinned, boolean colored) {
        return classResourcesFor(material, skinned, colored, false);
    }

    MaterialClassResources classResourcesFor(Material material, boolean skinned, boolean colored, boolean lightmapped) {
        long textureMask = texturePresenceMask(material) | (lightmapped ? LIGHTMAP_UV2_BIT : 0L);
        if (skinned || colored) {
            return classCache.computeIfAbsent(pipelineKey(material, skinned, colored) + "|tex" + textureMask,
                    ignored -> buildOrFallback(material, skinned, colored, textureMask));
        }
        ResolvedClass resolved = resolvedClasses.get(material);
        if (resolved != null && resolved.matches(material, textureMask)) {
            return resolved.resources();
        }
        MaterialClassResources built = classCache.computeIfAbsent(pipelineKey(material, false, false) + "|tex" + textureMask,
                ignored -> buildOrFallback(material, false, false, textureMask));
        resolvedClasses.put(material, new ResolvedClass(material.vertexShaderPath(),
                material.fragmentShaderPath(), surfaceShaderPathOf(material),
                material.blended(), material.doubleSided(), textureMask, built));
        return built;
    }

    private long texturePresenceMask(Material material) {
        Long cached = frameTexturePresenceMasks.get(material);
        if (cached != null) {
            return cached;
        }
        long computed = computeTexturePresenceMask(material);
        frameTexturePresenceMasks.put(material, computed);
        return computed;
    }

    private long computeTexturePresenceMask(Material material) {
        MaterialClassMetadata metadata = reflectionCache.computeIfAbsent(material.getClass(),
                materialClass -> MaterialClassMetadata.reflect(materialClass,
                        shaderLoader.load(material.fragmentShaderPath()).source()));
        long mask = 0L;
        List<TextureFieldDescriptor> fields = metadata.textureFields();
        for (int index = 0; index < fields.size(); index++) {
            if (metadata.readTexture(material, fields.get(index)) != null) {
                mask |= 1L << index;
            }
        }
        if (material instanceof LitMaterial lit && lit.alphaCutoff > 0.0f) {
            mask |= ALPHA_MASKED_BIT;
        }
        if (material instanceof LitMaterial lit && !lit.receiveShadows()) {
            mask |= NO_SHADOWS_BIT;
        }
        if (probeLightingActive) {
            mask |= PROBE_LIT_BIT;
        }
        return mask;
    }

    private static final long ALPHA_MASKED_BIT = 1L << 62;
    private static final long PROBE_LIT_BIT = 1L << 61;
    private static final long NO_SHADOWS_BIT = 1L << 60;
    private static final long LIGHTMAP_UV2_BIT = 1L << 59;

    private String textureDefines(Material material, long textureMask) {
        MaterialClassMetadata metadata = reflectionCache.get(material.getClass());
        if (metadata == null || textureMask == 0L) {
            return shadowFilterDefines();
        }
        StringBuilder defines = new StringBuilder(shadowFilterDefines());
        List<TextureFieldDescriptor> fields = metadata.textureFields();
        for (int index = 0; index < fields.size(); index++) {
            if ((textureMask & (1L << index)) != 0L) {
                defines.append("#define MATERIAL_HAS_")
                        .append(fields.get(index).reflectField().getName().toUpperCase(java.util.Locale.ROOT))
                        .append('\n');
            }
        }
        if ((textureMask & ALPHA_MASKED_BIT) != 0L) {
            defines.append("#define MATERIAL_ALPHA_MASKED\n");
        }
        if ((textureMask & PROBE_LIT_BIT) != 0L) {
            defines.append("#define PROBE_LIT\n");
        }
        if ((textureMask & NO_SHADOWS_BIT) != 0L) {
            defines.append("#define MATERIAL_NO_SHADOWS\n");
        }
        if ((textureMask & LIGHTMAP_UV2_BIT) != 0L) {
            defines.append("#define LIGHTMAP_UV2\n");
        }
        return defines.toString();
    }

    private static String shadowFilterDefines() {
        int samples = Math.clamp(Integer.getInteger("epysia.shadow.pcfSamples", 4), 1, 32);
        int filtered = Math.clamp(Integer.getInteger("epysia.shadow.pcfCascades", 2), 0, 4);
        return "#define CASCADE_PCF_SAMPLES " + samples + "\n"
                + "#define CASCADE_PCF_FILTERED_CASCADES " + filtered + "\n";
    }

    ParsedSource surfaceUniformsFor(Material material) {
        return classResourcesFor(material).surfaceUniforms();
    }

    private MaterialClassResources buildOrFallback(Material material, boolean skinned, boolean colored, long textureMask) {
        try {
            return buildClassResources(material, skinned, colored, textureMask);
        } catch (EpysiaException failure) {
            String surfacePath = surfaceShaderPathOf(material);
            if (surfacePath.isEmpty()) {
                throw failure;
            }
            logger.error("Surface shader '" + surfacePath
                    + "' failed to compile, falling back to the base material shaders", failure);
            return buildFallbackClassResources(material, surfacePath, skinned, colored, textureMask);
        }
    }

    private MaterialClassResources buildFallbackClassResources(Material material, String surfacePath,
                                                               boolean skinned, boolean colored, long textureMask) {
        String defines = textureDefines(material, textureMask);
        LoadedPrograms programs = loadPrograms(material.vertexShaderPath(), material.fragmentShaderPath(), "",
                skinned, colored, defines, (textureMask & LIGHTMAP_UV2_BIT) != 0L);
        MaterialClassMetadata metadata =
                MaterialClassMetadata.reflect(material.getClass(), programs.fragment().source());
        BindingSetLayout litLayout = buildLitBindingLayout(metadata, ParsedSource.empty(), skinned,
                (textureMask & PROBE_LIT_BIT) != 0L, (textureMask & LIGHTMAP_UV2_BIT) != 0L);
        PipelineHandle pipeline = backend.createPipeline(buildLitPipelineDescriptor(material, programs, litLayout, skinned, colored));
        registerHotReload(pipelineKey(material, skinned, colored) + "|tex" + textureMask, pipeline,
                material.vertexShaderPath(), material.fragmentShaderPath(), surfacePath,
                skinned, colored, defines, programs);
        return new MaterialClassResources(metadata, pipeline, litLayout, ParsedSource.empty(),
                supportsInstancing(material));
    }

    private static String pipelineKey(Material material, boolean skinned, boolean colored) {
        return material.getClass().getName() + "|" + material.vertexShaderPath() + "|" + material.fragmentShaderPath()
                + "|" + surfaceShaderPathOf(material)
                + "|" + (material.blended() ? "blended" : "opaque")
                + "|" + (material.doubleSided() ? "doubleSided" : "culled")
                + "|" + (skinned ? "skinned" : "static")
                + "|" + (colored ? "colored" : "plain");
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

    private MaterialClassResources buildClassResources(Material material, boolean skinned, boolean colored, long textureMask) {
        String defines = textureDefines(material, textureMask);
        LoadedPrograms programs = loadPrograms(material.vertexShaderPath(), material.fragmentShaderPath(),
                surfaceShaderPathOf(material), skinned, colored, defines, (textureMask & LIGHTMAP_UV2_BIT) != 0L);
        MaterialClassMetadata metadata = MaterialClassMetadata.reflect(material.getClass(), programs.fragment().source());
        ParsedSource surfaceUniforms = parseSurfaceUniforms(material);
        BindingSetLayout litLayout = buildLitBindingLayout(metadata, surfaceUniforms, skinned,
                (textureMask & PROBE_LIT_BIT) != 0L, (textureMask & LIGHTMAP_UV2_BIT) != 0L);
        PipelineHandle pipeline = backend.createPipeline(buildLitPipelineDescriptor(material, programs, litLayout, skinned, colored));
        registerHotReload(pipelineKey(material, skinned, colored) + "|tex" + textureMask, pipeline,
                material.vertexShaderPath(), material.fragmentShaderPath(), surfaceShaderPathOf(material),
                skinned, colored, defines, programs);
        return new MaterialClassResources(metadata, pipeline, litLayout, surfaceUniforms,
                supportsInstancing(material));
    }

    private static boolean supportsInstancing(Material material) {
        return !material.blended();
    }

    private ParsedSource parseSurfaceUniforms(Material material) {
        return parseSurfaceUniforms(material.vertexShaderPath(), material.fragmentShaderPath(),
                surfaceShaderPathOf(material));
    }

    private ParsedSource parseSurfaceUniforms(String vertexPath, String fragmentPath, String surfacePath) {
        if (!surfacePath.isEmpty()) {
            return SurfaceShaderComposer.parseUniforms(shaderLoader.load(surfacePath));
        }
        return ShaderUniformParser.merge(List.of(
                ShaderUniformParser.parse(shaderLoader.load(vertexPath).source()),
                ShaderUniformParser.parse(shaderLoader.load(fragmentPath).source())));
    }

    private LoadedPrograms loadPrograms(String vertexPath, String fragmentPath, String surfacePath,
                                        boolean skinned, boolean colored, String textureDefines) {
        return loadPrograms(vertexPath, fragmentPath, surfacePath, skinned, colored, textureDefines, false);
    }

    private LoadedPrograms loadPrograms(String vertexPath, String fragmentPath, String surfacePath,
                                        boolean skinned, boolean colored, String textureDefines,
                                        boolean lightmapUv2) {
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        if (surfacePath.isEmpty()) {
            ParsedSource declared = parseSurfaceUniforms(vertexPath, fragmentPath, "");
            vertex = SurfaceShaderComposer.injectUniformBlock(vertex, declared);
            fragment = SurfaceShaderComposer.injectUniformBlock(fragment, declared);
        } else {
            LoadedShader surface = shaderLoader.load(surfacePath);
            vertex = SurfaceShaderComposer.composeVertex(vertex, surface);
            fragment = SurfaceShaderComposer.composeFragment(fragment, surface);
        }
        if (skinned) {
            vertex = SurfaceShaderComposer.injectSkinningDefine(vertex);
        }
        if (colored) {
            vertex = SurfaceShaderComposer.injectVertexColoredDefine(vertex);
            fragment = SurfaceShaderComposer.injectVertexColoredDefine(fragment);
        }
        if (!textureDefines.isEmpty()) {
            fragment = SurfaceShaderComposer.injectDefineBlock(fragment, textureDefines);
        }
        if (lightmapUv2) {
            vertex = SurfaceShaderComposer.injectDefineBlock(vertex, "#define LIGHTMAP_UV2\n");
        }
        return new LoadedPrograms(vertex, fragment);
    }


    private BindingSetLayout buildLitBindingLayout(MaterialClassMetadata metadata, ParsedSource surfaceUniforms,
                                                   boolean skinned, boolean probeLit) {
        return buildLitBindingLayout(metadata, surfaceUniforms, skinned, probeLit, false);
    }

    private BindingSetLayout buildLitBindingLayout(MaterialClassMetadata metadata, ParsedSource surfaceUniforms,
                                                   boolean skinned, boolean probeLit, boolean lightmapUv2) {
        List<BindingSlot> slots = new ArrayList<>();
        slots.add(new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.LIGHT_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.CLUSTER_COUNT_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.CLUSTER_INDEX_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.INSTANCE_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.OPAQUE_COLOR_BINDING, BindingType.SAMPLED_TEXTURE_2D));
        slots.add(new BindingSlot(MeshShaderBindings.OPAQUE_DEPTH_BINDING, BindingType.SAMPLED_TEXTURE_2D));
        if (skinned) {
            slots.add(new BindingSlot(MeshShaderBindings.JOINT_PALETTE_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        }
        if (probeLit) {
            slots.add(new BindingSlot(MeshShaderBindings.PROBE_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        }
        if (lightmapUv2) {
            slots.add(new BindingSlot(MeshShaderBindings.LIGHTMAP_UV_SSBO_BINDING, BindingType.STORAGE_BUFFER));
        }
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
                                                          BindingSetLayout bindingLayout, boolean skinned, boolean colored) {
        List<VertexAttribute> attributes = new ArrayList<>(List.of(
                new VertexAttribute(0, VertexFormat.FLOAT3, 0),
                new VertexAttribute(1, VertexFormat.FLOAT3, 12),
                new VertexAttribute(2, VertexFormat.FLOAT2, 24),
                new VertexAttribute(3, VertexFormat.FLOAT3, 32)));
        int skinOffset = MeshShaderBindings.VERTEX_STRIDE;
        if (colored) {
            attributes.add(new VertexAttribute(6, VertexFormat.FLOAT4, MeshShaderBindings.VERTEX_STRIDE));
            skinOffset += MeshShaderBindings.VERTEX_COLOR_BYTES;
        }
        if (skinned) {
            attributes.add(new VertexAttribute(4, VertexFormat.UINT16X4, skinOffset));
            attributes.add(new VertexAttribute(5, VertexFormat.FLOAT4, skinOffset + 8));
        }
        VertexLayout layout = new VertexLayout(attributes, MeshShaderBindings.vertexStride(skinned, colored));
        return new PipelineDescriptor(programs.shaderSource(), layout, renderStateFor(material), bindingLayout);
    }

    private static RenderState renderStateFor(Material material) {
        RenderState base = material.blended() ? RenderState.TRANSPARENT_3D : RenderState.OPAQUE_3D;
        if (!material.doubleSided()) {
            return base;
        }
        return new RenderState(base.topology(), base.depthTest(), base.blendMode(), CullMode.NONE, base.depthWrite());
    }

    private void registerHotReload(String cacheKey, PipelineHandle pipeline, String vertexPath, String fragmentPath,
                                   String surfacePath, boolean skinned, boolean colored, String textureDefines,
                                   LoadedPrograms programs) {
        if (!shaderWatcher.active()) {
            return;
        }
        Set<String> dependencies = new LinkedHashSet<>();
        dependencies.addAll(programs.vertex().dependencyPaths());
        dependencies.addAll(programs.fragment().dependencyPaths());
        if (!surfacePath.isEmpty()) {
            dependencies.add(surfacePath);
        }
        shaderWatcher.watch(List.copyOf(dependencies), cacheKey,
                () -> reloadPipeline(cacheKey, pipeline, vertexPath, fragmentPath, surfacePath,
                        skinned, colored, textureDefines));
    }

    private void reloadPipeline(String cacheKey, PipelineHandle pipeline, String vertexPath, String fragmentPath,
                                String surfacePath, boolean skinned, boolean colored, String textureDefines) {
        if (!backend.isAlive(pipeline)) {
            return;
        }
        try {
            if (surfaceDeclarationsChanged(cacheKey, vertexPath, fragmentPath, surfacePath)) {
                evictForRebuild(cacheKey, surfacePath);
                return;
            }
            backend.updatePipelineShaders(pipeline,
                    loadPrograms(vertexPath, fragmentPath, surfacePath, skinned, colored, textureDefines).shaderSource());
            logger.info("Reloaded shader pipeline: " + vertexPath + " + " + fragmentPath
                    + (surfacePath.isEmpty() ? "" : " + " + surfacePath));
        } catch (EpysiaException exception) {
            logger.error("Shader reload failed for " + vertexPath + " + " + fragmentPath + ", keeping previous program", exception);
        }
    }

    private boolean surfaceDeclarationsChanged(String cacheKey, String vertexPath, String fragmentPath,
                                               String surfacePath) {
        MaterialClassResources cached = classCache.get(cacheKey);
        return cached != null && !cached.surfaceUniforms().declarations()
                .equals(parseSurfaceUniforms(vertexPath, fragmentPath, surfacePath).declarations());
    }

    private void evictForRebuild(String cacheKey, String surfacePath) {
        MaterialClassResources removed = classCache.remove(cacheKey);
        if (removed == null) {
            return;
        }
        resolvedClasses.values().removeIf(resolved -> resolved.resources() == removed);
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
