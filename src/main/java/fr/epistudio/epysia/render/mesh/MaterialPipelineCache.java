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
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

final class MaterialPipelineCache {
    public static final int MULTI_DRAW_INDEX_LOCATION = 7;

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;
    private static final int MATERIAL_UBO_SCRATCH_BYTES = 1024;
    private final ByteBuffer scratchMaterialUbo = BufferUtils.createByteBuffer(MATERIAL_UBO_SCRATCH_BYTES);
    private final Map<String, MaterialClassResources> classCache = new HashMap<>();
    private final Map<Class<? extends Material>, MaterialClassMetadata> reflectionCache = new HashMap<>();
    private final Map<Material, ResolvedClass> resolvedClasses = new IdentityHashMap<>();
    private static final byte[] NO_UNIFORM_BYTES = new byte[0];

    private final Map<Material, MaterialUniformState> uniformStates = new IdentityHashMap<>();
    private static final byte[] ZERO_BYTES = new byte[MATERIAL_UBO_SCRATCH_BYTES];
    private byte[] scratchUniformBytes = new byte[256];
    private long uniformFrameCounter;
    private final List<BufferHandle> ownedBuffers = new ArrayList<>();

    private BooleanSupplier depthPrepassEnabled = () -> false;
    private RenderBackend backend;
    private TextureHandle defaultAlbedo;
    private TextureHandle defaultNormalMap;
    private boolean probeLightingActive;

    MaterialPipelineCache(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.shaderWatcher = shaderWatcher;
        this.logger = logger;
    }

    void useDepthPrepassState(BooleanSupplier enabled) {
        this.depthPrepassEnabled = enabled;
    }

    boolean depthPrepassCovers(Material material, boolean skinned) {
        if (!depthPrepassEnabled.getAsBoolean() || skinned || material.blended()) {
            return false;
        }
        return !material.alphaScissor() || shadowMasked(material);
    }

    private boolean shadowMasked(Material material) {
        return material instanceof LitMaterial lit && lit.alphaCutoff > 0.0f
                && materialClassHasUniformBuffer(material);
    }

    private boolean materialClassHasUniformBuffer(Material material) {
        MaterialClassMetadata metadata = reflectionCache.get(material.getClass());
        return metadata != null && metadata.hasUniformBuffer();
    }

    void initialize(RenderBackend backend) {
        this.backend = backend;
        defaultAlbedo = Texture2D.whitePixel(backend);
        defaultNormalMap = createFlatNormalTexture(backend);
    }

    void beginFrame() {
        uniformFrameCounter++;
        frameTextures.clear();
    }

    private final Map<Material, FrameTextures> frameTextures = new IdentityHashMap<>();

    private record FrameTextures(TextureHandle[] resolved, long presenceMask) {
    }

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
        return classResourcesFor(material, skinned, colored, lightmapped, false);
    }

    MaterialClassResources classResourcesFor(Material material, boolean skinned, boolean colored,
                                             boolean lightmapped, boolean multiDraw) {
        long textureMask = texturePresenceMask(material) | (lightmapped ? LIGHTMAP_UV2_BIT : 0L);
        if (multiDraw) {
            return classCache.computeIfAbsent(pipelineKey(material, skinned, colored, true) + "|tex" + textureMask,
                    ignored -> buildOrFallback(material, skinned, colored, textureMask, true));
        }
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
        return frameTexturesFor(material).presenceMask;
    }

    TextureHandle[] resolvedTextures(Material material) {
        return frameTexturesFor(material).resolved;
    }

    private FrameTextures frameTexturesFor(Material material) {
        FrameTextures cached = frameTextures.get(material);
        if (cached != null) {
            return cached;
        }
        FrameTextures computed = readTextures(material);
        frameTextures.put(material, computed);
        return computed;
    }

    private FrameTextures readTextures(Material material) {
        MaterialClassMetadata metadata = reflectionCache.computeIfAbsent(material.getClass(),
                materialClass -> MaterialClassMetadata.reflect(materialClass,
                        shaderLoader.load(material.fragmentShaderPath()).source()));
        List<TextureFieldDescriptor> fields = metadata.textureFields();
        TextureHandle[] resolved = new TextureHandle[fields.size()];
        long mask = 0L;
        for (int index = 0; index < fields.size(); index++) {
            TextureHandle handle = metadata.readTexture(material, fields.get(index));
            if (handle != null) {
                mask |= 1L << index;
            }
            resolved[index] = handle != null ? handle : defaultFor(fields.get(index));
        }
        return new FrameTextures(resolved, maskWithMaterialBits(material, mask));
    }

    private long maskWithMaterialBits(Material material, long textureBits) {
        long mask = textureBits;
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

    private String textureDefines(Material material, long textureMask, boolean skinned) {
        MaterialClassMetadata metadata = reflectionCache.get(material.getClass());
        if (metadata == null || textureMask == 0L) {
            return shadowFilterDefines();
        }
        StringBuilder defines = new StringBuilder(shadowFilterDefines());
        if (material.alphaScissor() && depthPrepassCovers(material, skinned)) {
            defines.append("#define MATERIAL_EARLY_DEPTH_TESTED\n");
        }
        List<TextureFieldDescriptor> fields = metadata.textureFields();
        for (int index = 0; index < fields.size(); index++) {
            if ((textureMask & (1L << index)) != 0L) {
                defines.append("#define MATERIAL_HAS_")
                        .append(fields.get(index).reflectField().getName().toUpperCase(Locale.ROOT))
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
        return buildOrFallback(material, skinned, colored, textureMask, false);
    }

    private MaterialClassResources buildOrFallback(Material material, boolean skinned, boolean colored,
                                                   long textureMask, boolean multiDraw) {
        try {
            return buildClassResources(material, skinned, colored, textureMask, multiDraw);
        } catch (EpysiaException failure) {
            String surfacePath = surfaceShaderPathOf(material);
            if (surfacePath.isEmpty()) {
                throw failure;
            }
            logger.error("Surface shader '" + surfacePath
                    + "' failed to compile, falling back to the base material shaders", failure);
            return buildFallbackClassResources(material, surfacePath, skinned, colored, textureMask, multiDraw);
        }
    }

    private MaterialClassResources buildFallbackClassResources(Material material, String surfacePath,
                                                               boolean skinned, boolean colored, long textureMask) {
        return buildFallbackClassResources(material, surfacePath, skinned, colored, textureMask, false);
    }

    private MaterialClassResources buildFallbackClassResources(Material material, String surfacePath,
                                                               boolean skinned, boolean colored,
                                                               long textureMask, boolean multiDraw) {
        String defines = textureDefines(material, textureMask, skinned);
        LoadedPrograms programs = loadPrograms(material.vertexShaderPath(), material.fragmentShaderPath(), "",
                skinned, colored, defines, (textureMask & LIGHTMAP_UV2_BIT) != 0L);
        MaterialClassMetadata metadata =
                MaterialClassMetadata.reflect(material.getClass(), programs.fragment().source());
        BindingSetLayout litLayout = buildLitBindingLayout(metadata, ParsedSource.empty(), skinned,
                (textureMask & PROBE_LIT_BIT) != 0L, (textureMask & LIGHTMAP_UV2_BIT) != 0L);
        PipelineHandle pipeline = backend.createPipeline(
                buildLitPipelineDescriptor(material, programs, litLayout, skinned, colored, multiDraw));
        registerHotReload(pipelineKey(material, skinned, colored, multiDraw) + "|tex" + textureMask, pipeline,
                material.vertexShaderPath(), material.fragmentShaderPath(), surfacePath,
                skinned, colored, defines, programs);
        return new MaterialClassResources(metadata, pipeline, litLayout, ParsedSource.empty(),
                supportsInstancing(material));
    }

    private String pipelineKey(Material material, boolean skinned, boolean colored) {
        return pipelineKey(material, skinned, colored, false);
    }

    private String pipelineKey(Material material, boolean skinned, boolean colored, boolean multiDraw) {
        boolean prepassCovered = depthPrepassCovers(material, skinned);
        return material.getClass().getName() + "|" + material.vertexShaderPath() + "|" + material.fragmentShaderPath()
                + "|" + surfaceShaderPathOf(material)
                + "|" + (material.blended() ? "blended" : "opaque")
                + "|" + (material.doubleSided() ? "doubleSided" : "culled")
                + "|" + (skinned ? "skinned" : "static")
                + "|" + (colored ? "colored" : "plain")
                + "|" + (multiDraw ? "multiDraw" : "singleDraw")
                + "|" + (prepassCovered ? "prepassCovered" : "writesDepth");
    }

    static String surfaceShaderPathOf(Material material) {
        return material instanceof LitMaterial lit ? lit.surfaceShaderPath() : "";
    }

    BufferHandle ensureMaterialUbo(Material material, MaterialClassResources classResources) {
        if (!classResources.metadata().hasUniformBuffer()) {
            return null;
        }
        MaterialUniformState state = stateFor(material);
        if (state.buffer != null) {
            return state.buffer;
        }
        ByteBuffer empty = BufferUtils.createByteBuffer(classResources.metadata().uniformBufferSize());
        state.buffer = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, empty));
        ownedBuffers.add(state.buffer);
        return state.buffer;
    }

    private MaterialUniformState stateFor(Material material) {
        MaterialUniformState existing = uniformStates.get(material);
        if (existing != null) {
            return existing;
        }
        MaterialUniformState created = new MaterialUniformState();
        uniformStates.put(material, created);
        return created;
    }

    BufferHandle materialUboFor(Material material) {
        MaterialUniformState state = uniformStates.get(material);
        return state == null ? null : state.buffer;
    }

    void writeMaterialUboIfNeeded(Material material, MaterialClassResources classResources) {
        if (material == null || !classResources.metadata().hasUniformBuffer()) {
            return;
        }
        MaterialUniformState state = uniformStates.get(material);
        if (state == null || state.buffer == null || state.lastPackedFrame == uniformFrameCounter) {
            return;
        }
        state.lastPackedFrame = uniformFrameCounter;
        int size = classResources.metadata().uniformBufferSize();
        packUniforms(material, classResources, size);
        if (state.matches(scratchUniformBytes, size)) {
            return;
        }
        state.adopt(scratchUniformBytes, size);
        backend.writeBuffer(state.buffer, scratchMaterialUbo, 0L);
    }

    private void packUniforms(Material material, MaterialClassResources classResources, int size) {
        ensureScratchUniformBytes(size);
        scratchMaterialUbo.clear();
        scratchMaterialUbo.limit(size);
        scratchMaterialUbo.put(ZERO_BYTES, 0, size);
        scratchMaterialUbo.position(0);
        classResources.metadata().writeUniformBuffer(material, scratchMaterialUbo);
        scratchMaterialUbo.position(0);
        scratchMaterialUbo.limit(size);
        scratchMaterialUbo.get(scratchUniformBytes, 0, size);
        scratchMaterialUbo.position(0);
    }

    private void ensureScratchUniformBytes(int size) {
        if (scratchUniformBytes.length < size) {
            scratchUniformBytes = new byte[size];
        }
    }

    private static final class MaterialUniformState {
        private BufferHandle buffer;
        private byte[] snapshot = NO_UNIFORM_BYTES;
        private long lastPackedFrame = -1L;

        private boolean matches(byte[] candidate, int size) {
            return snapshot.length == size && Arrays.equals(snapshot, 0, size, candidate, 0, size);
        }

        private void adopt(byte[] candidate, int size) {
            if (snapshot.length != size) {
                snapshot = new byte[size];
            }
            System.arraycopy(candidate, 0, snapshot, 0, size);
        }
    }

    byte[] uniformSnapshotOf(Material material) {
        MaterialUniformState state = uniformStates.get(material);
        return state == null ? NO_UNIFORM_BYTES : state.snapshot;
    }

    TextureHandle defaultFor(TextureFieldDescriptor field) {
        return field.normalMapLike() ? defaultNormalMap : defaultAlbedo;
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
        uniformStates.clear();
        classCache.clear();
        resolvedClasses.clear();
    }

    private MaterialClassResources buildClassResources(Material material, boolean skinned, boolean colored, long textureMask) {
        return buildClassResources(material, skinned, colored, textureMask, false);
    }

    private MaterialClassResources buildClassResources(Material material, boolean skinned, boolean colored,
                                                       long textureMask, boolean multiDraw) {
        String defines = textureDefines(material, textureMask, skinned);
        LoadedPrograms programs = loadPrograms(material.vertexShaderPath(), material.fragmentShaderPath(),
                surfaceShaderPathOf(material), skinned, colored, defines,
                (textureMask & LIGHTMAP_UV2_BIT) != 0L, multiDraw);
        MaterialClassMetadata metadata = MaterialClassMetadata.reflect(material.getClass(), programs.fragment().source());
        ParsedSource surfaceUniforms = parseSurfaceUniforms(material);
        BindingSetLayout litLayout = buildLitBindingLayout(metadata, surfaceUniforms, skinned,
                (textureMask & PROBE_LIT_BIT) != 0L, (textureMask & LIGHTMAP_UV2_BIT) != 0L);
        PipelineHandle pipeline = backend.createPipeline(
                buildLitPipelineDescriptor(material, programs, litLayout, skinned, colored, multiDraw));
        registerHotReload(pipelineKey(material, skinned, colored, multiDraw) + "|tex" + textureMask, pipeline,
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
        return loadPrograms(vertexPath, fragmentPath, surfacePath, skinned, colored,
                textureDefines, lightmapUv2, false);
    }

    private LoadedPrograms loadPrograms(String vertexPath, String fragmentPath, String surfacePath,
                                        boolean skinned, boolean colored, String textureDefines,
                                        boolean lightmapUv2, boolean multiDraw) {
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
        if (multiDraw) {
            vertex = SurfaceShaderComposer.injectMultiDrawDefine(vertex);
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
                                                          BindingSetLayout bindingLayout, boolean skinned,
                                                          boolean colored, boolean multiDraw) {
        PipelineDescriptor single = buildLitPipelineDescriptor(material, programs, bindingLayout, skinned, colored);
        if (!multiDraw) {
            return single;
        }
        VertexLayout perDraw = new VertexLayout(
                List.of(new VertexAttribute(MULTI_DRAW_INDEX_LOCATION, VertexFormat.UINT32, 0)), Integer.BYTES);
        return new PipelineDescriptor(single.shaders(), single.vertexLayout(), single.state(),
                single.bindingLayout(), perDraw);
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
        return new PipelineDescriptor(programs.shaderSource(), layout,
                renderStateFor(material, skinned), bindingLayout);
    }

    private RenderState renderStateFor(Material material, boolean skinned) {
        RenderState base = material.blended() ? RenderState.TRANSPARENT_3D : RenderState.OPAQUE_3D;
        boolean writesDepth = base.depthWrite() && !depthPrepassCovers(material, skinned);
        CullMode cullMode = material.doubleSided() ? CullMode.NONE : base.cullMode();
        return new RenderState(base.topology(), base.depthTest(), base.blendMode(), cullMode, writesDepth);
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
