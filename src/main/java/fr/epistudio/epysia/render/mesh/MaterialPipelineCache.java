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
import fr.epistudio.epysia.render.material.Material;
import fr.epistudio.epysia.render.material.MaterialClassMetadata;
import fr.epistudio.epysia.render.material.MaterialClassMetadata.TextureFieldDescriptor;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
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
import java.util.Set;

final class MaterialPipelineCache {

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Logger logger;
    private final ByteBuffer scratchMaterialUbo = BufferUtils.createByteBuffer(1024);
    private final Map<String, MaterialClassResources> classCache = new HashMap<>();
    private final Map<Material, BufferHandle> materialUbos = new IdentityHashMap<>();
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

    MaterialClassResources classResourcesFor(Material material) {
        return classCache.computeIfAbsent(pipelineKey(material), ignored -> buildClassResources(material));
    }

    private static String pipelineKey(Material material) {
        return material.getClass().getName() + "|" + material.vertexShaderPath() + "|" + material.fragmentShaderPath()
                + "|" + (material.transparent() ? "transparent" : "opaque")
                + "|" + (material.doubleSided() ? "doubleSided" : "culled");
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
        backend.writeBuffer(materialUbo, scratchMaterialUbo, 0L);
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
        writtenThisFrame.clear();
    }

    private MaterialClassResources buildClassResources(Material material) {
        LoadedShader fragmentLoaded = shaderLoader.load(material.fragmentShaderPath());
        MaterialClassMetadata metadata = MaterialClassMetadata.reflect(material.getClass(), fragmentLoaded.source());
        BindingSetLayout litLayout = buildLitBindingLayout(metadata);
        PipelineHandle pipeline = backend.createPipeline(buildLitPipelineDescriptor(material, litLayout));
        registerHotReload(pipeline, material.vertexShaderPath(), material.fragmentShaderPath());
        return new MaterialClassResources(metadata, pipeline, litLayout);
    }

    private BindingSetLayout buildLitBindingLayout(MaterialClassMetadata metadata) {
        List<BindingSlot> slots = new ArrayList<>();
        slots.add(new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER));
        slots.add(new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER));
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
        return new BindingSetLayout(slots);
    }

    private PipelineDescriptor buildLitPipelineDescriptor(Material material, BindingSetLayout bindingLayout) {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexAttribute normal = new VertexAttribute(1, VertexFormat.FLOAT3, 12);
        VertexAttribute uv = new VertexAttribute(2, VertexFormat.FLOAT2, 24);
        VertexAttribute tangent = new VertexAttribute(3, VertexFormat.FLOAT3, 32);
        VertexLayout layout = new VertexLayout(List.of(position, normal, uv, tangent), MeshShaderBindings.VERTEX_STRIDE);
        RenderState state = renderStateFor(material);
        return new PipelineDescriptor(
                loadShaderSource(material.vertexShaderPath(), material.fragmentShaderPath()),
                layout,
                state,
                bindingLayout
        );
    }

    private static RenderState renderStateFor(Material material) {
        RenderState base = material.transparent() ? RenderState.TRANSPARENT_3D : RenderState.OPAQUE_3D;
        if (!material.doubleSided()) {
            return base;
        }
        return new RenderState(base.topology(), base.depthTest(), base.blendMode(), CullMode.NONE, base.depthWrite());
    }

    private ShaderSource loadShaderSource(String vertexPath, String fragmentPath) {
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    private void registerHotReload(PipelineHandle pipeline, String vertexPath, String fragmentPath) {
        if (!shaderWatcher.active()) {
            return;
        }
        Set<String> dependencies = new LinkedHashSet<>();
        dependencies.addAll(shaderLoader.load(vertexPath).dependencyPaths());
        dependencies.addAll(shaderLoader.load(fragmentPath).dependencyPaths());
        shaderWatcher.watch(List.copyOf(dependencies),
                () -> reloadPipeline(pipeline, vertexPath, fragmentPath));
    }

    private void reloadPipeline(PipelineHandle pipeline, String vertexPath, String fragmentPath) {
        try {
            backend.updatePipelineShaders(pipeline, loadShaderSource(vertexPath, fragmentPath));
            logger.info("Reloaded shader pipeline: " + vertexPath + " + " + fragmentPath);
        } catch (EpysiaException exception) {
            logger.error("Shader reload failed for " + vertexPath + " + " + fragmentPath + ", keeping previous program", exception);
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
