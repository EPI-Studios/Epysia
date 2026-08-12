package fr.epistudio.epysia.render.sprite;

import fr.epistudio.epysia.components.SpriteRenderer;
import fr.epistudio.epysia.components.transforms.Transform2D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.RenderSystem;
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
import fr.epistudio.epysia.render.backend.IndexFormat;
import fr.epistudio.epysia.render.backend.MeshDescriptor;
import fr.epistudio.epysia.render.backend.MeshHandle;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.mesh.SurfaceUniformBinder;
import fr.epistudio.epysia.render.shader.ShaderUniformParser.ParsedSource;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.shader.SurfaceShaderComposer;
import fr.epistudio.epysia.render.texture.Texture2D;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SpriteRenderSystem implements RenderSystem {
    private static final String VERTEX_PATH = "sprite.vert.glsl";
    private static final String FRAGMENT_PATH = "sprite.frag.glsl";
    private static final String LIT_VERTEX_PATH = "sprite_lit.vert.glsl";
    private static final String LIT_FRAGMENT_PATH = "sprite_lit.frag.glsl";
    static final int VERTICES_PER_QUAD = 4;
    static final int INDICES_PER_QUAD = 6;
    static final int VERTEX_BYTES = 96;
    private static final int INITIAL_QUAD_CAPACITY = 1024;

    private record SpriteEntry(Transform2D transform, SpriteRenderer sprite, TextureHandle texture) {
        boolean lit() {
            return sprite.lit();
        }

        int effectiveOrder() {
            return sprite.sortByY()
                    ? SpriteSortKeys.orderFromWorldY(transform.worldPosition(new Vector2f()).y)
                    : sprite.orderInLayer();
        }
    }

    private record TextureSet(TextureHandle albedo, TextureHandle normal,
                              TextureHandle metallicRoughness, TextureHandle emissive) {
    }

    private record PipelineKey(String surfaceShaderPath, boolean lit) {
    }

    private static final boolean GROUP_BY_TEXTURE =
            Boolean.parseBoolean(System.getProperty("epysia.sprite.groupByTexture", "true"));

    private static final Comparator<SpriteEntry> PAINTER_ORDER = painterOrder();

    private static Comparator<SpriteEntry> painterOrder() {
        Comparator<SpriteEntry> order = Comparator
                .comparing((SpriteEntry entry) -> entry.lit())
                .thenComparingInt(entry -> entry.sprite().sortingLayer())
                .thenComparingInt(SpriteEntry::effectiveOrder)
                .thenComparingInt(entry -> entry.transform().renderLayer());
        return GROUP_BY_TEXTURE ? order.thenComparingLong(entry -> entry.texture().id()) : order;
    }

    private int batchesThisFrame;

    public int batchesThisFrame() {
        return batchesThisFrame;
    }

    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private final Set<PipelineKey> staleKeys = ConcurrentHashMap.newKeySet();
    private final MeshRenderSystem meshRenderSystem;
    private final Logger logger;
    private final List<SpriteEntry> entries = new ArrayList<>();
    private final Map<BindingKey, BindingSetHandle> bindingsByTexture = new HashMap<>();
    private final Map<LitBindingKey, BindingSetHandle> litBindingsByTextures = new HashMap<>();
    private final Light2dStorage light2dStorage = new Light2dStorage();
    private final Map<PipelineKey, PipelineHandle> pipelinesByKey = new HashMap<>();
    private final Map<PipelineKey, ParsedSource> parsedByKey = new HashMap<>();
    private final Map<SpriteRenderer, BindingSetHandle> uniformBindingsBySprite = new HashMap<>();
    private final SurfaceUniformBinder surfaceUniforms;
    private final Map<Integer, MeshHandle> meshesByFirstIndex = new HashMap<>();
    private final Vector2f scratchCorner = new Vector2f();

    private RenderBackend backend;
    private BindingSetLayout bindingLayout;
    private BindingSetLayout litBindingLayout;
    private PipelineHandle pipeline;
    private PipelineHandle litPipeline;
    private TextureHandle flatNormalTexture;
    private TextureHandle whiteTexture;
    private TextureHandle blackTexture;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private ByteBuffer vertexScratch;
    private Object2dUniform identityObject;
    private int quadCapacity;

    public SpriteRenderSystem(ShaderLoader shaderLoader, MeshRenderSystem meshRenderSystem, Logger logger) {
        this(shaderLoader, new ShaderWatcher(shaderLoader.filesystemRoot()), meshRenderSystem, logger);
    }

    public SpriteRenderSystem(ShaderLoader shaderLoader, ShaderWatcher shaderWatcher,
                              MeshRenderSystem meshRenderSystem, Logger logger) {
        this.shaderWatcher = shaderWatcher;
        this.shaderLoader = shaderLoader;
        this.surfaceUniforms = new SurfaceUniformBinder(logger);
        this.meshRenderSystem = meshRenderSystem;
        this.logger = logger;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        bindingLayout = new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(Object2dUniform.BINDING, BindingType.UNIFORM_BUFFER)
        ));
        litBindingLayout = new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(2, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(3, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(4, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(Object2dUniform.BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(6, BindingType.STORAGE_BUFFER)
        ));
        identityObject = new Object2dUniform(backend);
        pipeline = backend.createPipeline(buildPipelineDescriptor());
        litPipeline = backend.createPipeline(buildLitPipelineDescriptor());
        light2dStorage.initialize(backend);
        surfaceUniforms.initialize(backend);
        flatNormalTexture = Texture2D.solidColor(backend, 128, 128, 255);
        whiteTexture = Texture2D.whitePixel(backend);
        blackTexture = Texture2D.solidColor(backend, 0, 0, 0);
        allocateBuffers(INITIAL_QUAD_CAPACITY);
    }

    private PipelineDescriptor buildLitPipelineDescriptor() {
        LoadedShader vertex = shaderLoader.load(LIT_VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(LIT_FRAGMENT_PATH);
        return new PipelineDescriptor(new ShaderSource(vertex.source(), fragment.source()),
                spriteVertexLayout(), RenderState.SPRITE_2D, litBindingLayout);
    }

    private static VertexLayout spriteVertexLayout() {
        return new VertexLayout(List.of(
                new VertexAttribute(0, VertexFormat.FLOAT2, 0),
                new VertexAttribute(1, VertexFormat.FLOAT2, 8),
                new VertexAttribute(2, VertexFormat.FLOAT4, 16),
                new VertexAttribute(3, VertexFormat.FLOAT4, 32),
                new VertexAttribute(4, VertexFormat.FLOAT4, 48),
                new VertexAttribute(5, VertexFormat.FLOAT4, 64),
                new VertexAttribute(6, VertexFormat.FLOAT4, 80)), VERTEX_BYTES);
    }

    private PipelineDescriptor buildPipelineDescriptor() {
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        return new PipelineDescriptor(new ShaderSource(vertex.source(), fragment.source()),
                spriteVertexLayout(), RenderState.SPRITE_2D, bindingLayout);
    }

    private void allocateBuffers(int quadCount) {
        quadCapacity = quadCount;
        vertexScratch = BufferUtils.createByteBuffer(quadCount * VERTICES_PER_QUAD * VERTEX_BYTES);
        ByteBuffer vertexInit = BufferUtils.createByteBuffer(quadCount * VERTICES_PER_QUAD * VERTEX_BYTES);
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertexInit));
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, buildIndexPattern(quadCount)));
    }

    static ByteBuffer buildIndexPattern(int quadCount) {
        ByteBuffer indices = BufferUtils.createByteBuffer(quadCount * INDICES_PER_QUAD * Integer.BYTES);
        for (int quad = 0; quad < quadCount; quad++) {
            int base = quad * VERTICES_PER_QUAD;
            indices.putInt(base).putInt(base + 1).putInt(base + 2);
            indices.putInt(base).putInt(base + 2).putInt(base + 3);
        }
        return indices.flip();
    }

    private void ensureCapacity(int quadCount) {
        if (quadCount <= quadCapacity) {
            return;
        }
        int grownCapacity = Math.max(quadCapacity * 2, quadCount);
        destroyMeshes();
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        allocateBuffers(grownCapacity);
        logger.info("[SpriteRenderSystem] grew quad capacity to " + grownCapacity);
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        entries.clear();
        shaderWatcher.poll();
        surfaceUniforms.beginFrame();
        rebuildStalePipelines();
        light2dStorage.update(scene);
        gatherSprites(scene);
        if (entries.isEmpty()) {
            return;
        }
        entries.sort(PAINTER_ORDER);
        ensureCapacity(entries.size());
        vertexScratch.clear();
        for (int i = 0; i < entries.size(); i++) {
            appendSprite(entries.get(i));
        }
        vertexScratch.flip();
        backend.writeBuffer(vertexBuffer, vertexScratch, 0L);
        submitBatches(frame);
    }

    private void gatherSprites(Scene scene) {
        for (SpriteRenderer sprite : scene.componentsOf(SpriteRenderer.class)) {
            if (!sprite.activeInHierarchy()) {
                continue;
            }
            Transform2D transform = sprite.owner()
                    .map(owner -> owner.getComponentOrNull(Transform2D.class))
                    .orElse(null);
            if (transform == null || !transform.visible()) {
                continue;
            }
            sprite.texture().ifPresent(texture -> entries.add(new SpriteEntry(transform, sprite, texture)));
        }
    }

    private void appendSprite(SpriteEntry entry) {
        SpriteRenderer sprite = entry.sprite();
        float pixelWidth = backend.textureWidth(entry.texture()) * (sprite.regionMaxU() - sprite.regionMinU());
        float pixelHeight = backend.textureHeight(entry.texture()) * (sprite.regionMaxV() - sprite.regionMinV());
        float halfWidth = Math.abs(pixelWidth) / sprite.pixelsPerUnit() * 0.5f;
        float halfHeight = Math.abs(pixelHeight) / sprite.pixelsPerUnit() * 0.5f;
        float leftU = sprite.flipX() ? sprite.regionMaxU() : sprite.regionMinU();
        float rightU = sprite.flipX() ? sprite.regionMinU() : sprite.regionMaxU();
        float bottomV = sprite.flipY() ? sprite.regionMaxV() : sprite.regionMinV();
        float topV = sprite.flipY() ? sprite.regionMinV() : sprite.regionMaxV();
        float centerX = mirroredCenter(sprite, sprite.flipX(), entry.transform().pivot().x);
        float centerY = mirroredCenter(sprite, sprite.flipY(), entry.transform().pivot().y);
        Matrix3x2f matrix = entry.transform().worldMatrix();
        appendVertex(matrix, centerX - halfWidth, centerY - halfHeight, leftU, bottomV, sprite);
        appendVertex(matrix, centerX + halfWidth, centerY - halfHeight, rightU, bottomV, sprite);
        appendVertex(matrix, centerX + halfWidth, centerY + halfHeight, rightU, topV, sprite);
        appendVertex(matrix, centerX - halfWidth, centerY + halfHeight, leftU, topV, sprite);
    }

    private static float mirroredCenter(SpriteRenderer sprite, boolean flipped, float pivotComponent) {
        return flipped && sprite.flipAroundPivot() ? 2.0f * pivotComponent : 0.0f;
    }

    private void appendVertex(Matrix3x2f matrix, float cornerX, float cornerY, float u, float v, SpriteRenderer sprite) {
        scratchCorner.set(cornerX, cornerY);
        matrix.transformPosition(scratchCorner);
        Vector3f tint = sprite.tint();
        vertexScratch.putFloat(scratchCorner.x).putFloat(scratchCorner.y);
        vertexScratch.putFloat(u).putFloat(v);
        vertexScratch.putFloat(tint.x).putFloat(tint.y).putFloat(tint.z).putFloat(sprite.opacity());
        vertexScratch.putFloat(sprite.metallic()).putFloat(sprite.roughness())
                .putFloat(sprite.normalStrength()).putFloat(sprite.emissiveStrength());
        vertexScratch.putFloat(sprite.flipX() ? -1.0f : 1.0f).putFloat(sprite.flipY() ? -1.0f : 1.0f)
                .putFloat(sprite.lightLayers()).putFloat(0.0f);
        putVector4(sprite.shaderParams0());
        putVector4(sprite.shaderParams1());
    }

    private void putVector4(Vector4f value) {
        vertexScratch.putFloat(value.x).putFloat(value.y).putFloat(value.z).putFloat(value.w);
    }

    private void submitBatches(FrameBuilder frame) {
        int batchStart = 0;
        long sequence = 0L;
        batchesThisFrame = 0;
        for (int i = 1; i <= entries.size(); i++) {
            if (i < entries.size() && sameBatch(entries.get(i), entries.get(batchStart))) {
                continue;
            }
            frame.submit(RenderPasses.OVERLAY_2D, batchCommand(batchStart, i - batchStart, sequence));
            sequence++;
            batchesThisFrame++;
            batchStart = i;
        }
    }

    private boolean sameBatch(SpriteEntry candidate, SpriteEntry reference) {
        if (parsedFor(reference.sprite()).hasBufferDeclarations()) {
            return candidate.sprite() == reference.sprite();
        }
        return candidate.lit() == reference.lit()
                && candidate.sprite().surfaceShaderPath().equals(reference.sprite().surfaceShaderPath())
                && textureSetOf(candidate).equals(textureSetOf(reference));
    }

    private BindingSetLayout surfaceLayoutFor(PipelineKey key, ParsedSource parsed) {
        BindingSetLayout base = key.lit() ? litBindingLayout : bindingLayout;
        if (!parsed.hasBufferDeclarations()) {
            return base;
        }
        List<BindingSlot> slots = new ArrayList<>(base.slots());
        SurfaceUniformBinder.appendSlots(slots, parsed);
        return new BindingSetLayout(slots);
    }

    private ParsedSource parsedFor(SpriteRenderer sprite) {
        if (sprite.surfaceShaderPath().isEmpty()) {
            return ParsedSource.empty();
        }
        pipelineFor(sprite);
        return parsedByKey.getOrDefault(new PipelineKey(sprite.surfaceShaderPath(), sprite.lit()),
                ParsedSource.empty());
    }

    private void rebuildStalePipelines() {
        if (staleKeys.isEmpty()) {
            return;
        }
        uniformBindingsBySprite.values().forEach(backend::destroy);
        uniformBindingsBySprite.clear();
        for (PipelineKey key : Set.copyOf(staleKeys)) {
            staleKeys.remove(key);
            parsedByKey.remove(key);
            PipelineHandle previous = pipelinesByKey.remove(key);
            if (previous != null) {
                backend.destroy(previous);
            }
            logger.info("[SpriteRenderSystem] reloaded surface shader " + key.surfaceShaderPath());
        }
    }

    private PipelineHandle pipelineFor(SpriteRenderer sprite) {
        String surfacePath = sprite.surfaceShaderPath();
        if (surfacePath.isEmpty()) {
            return sprite.lit() ? litPipeline : pipeline;
        }
        return pipelinesByKey.computeIfAbsent(new PipelineKey(surfacePath, sprite.lit()),
                key -> createSurfacePipeline(key));
    }

    private PipelineHandle createSurfacePipeline(PipelineKey key) {
        LoadedShader vertex = shaderLoader.load(key.lit() ? LIT_VERTEX_PATH : VERTEX_PATH);
        LoadedShader base = shaderLoader.load(key.lit() ? LIT_FRAGMENT_PATH : FRAGMENT_PATH);
        LoadedShader surface = shaderLoader.load(key.surfaceShaderPath());
        LoadedShader composed = SurfaceShaderComposer.composeSpriteFragment(base, surface);
        shaderWatcher.watch(surface.dependencyPaths(), "sprite:" + key, () -> staleKeys.add(key));
        ParsedSource parsed = SurfaceShaderComposer.parseUniforms(surface);
        parsedByKey.put(key, parsed);
        return backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(vertex.source(), composed.source()),
                spriteVertexLayout(), RenderState.SPRITE_2D,
                surfaceLayoutFor(key, parsed)));
    }

    private TextureSet textureSetOf(SpriteEntry entry) {
        SpriteRenderer sprite = entry.sprite();
        return new TextureSet(entry.texture(),
                sprite.normalMapRef().direct().orElse(flatNormalTexture),
                sprite.metallicRoughnessMapRef().direct().orElse(whiteTexture),
                sprite.emissiveMapRef().direct().orElse(blackTexture));
    }

    private DrawCommand batchCommand(int firstQuad, int quadCount, long sequence) {
        MeshHandle mesh = meshForFirstIndex(firstQuad * INDICES_PER_QUAD);
        SpriteRenderer sprite = entries.get(firstQuad).sprite();
        long sortKey = SpriteSortKeys.compose(sprite.sortingLayer(),
                entries.get(firstQuad).effectiveOrder(), SpriteSortKeys.KIND_SPRITE, sequence);
        SpriteEntry first = entries.get(firstQuad);
        PipelineHandle selected = pipelineFor(first.sprite());
        ParsedSource parsed = parsedFor(first.sprite());
        BindingSetHandle bindings = parsed.hasBufferDeclarations()
                ? uniformBindingsFor(first, parsed)
                : (first.lit() ? litBindingsFor(textureSetOf(first), identityObject.handle())
                        : bindingsFor(first.texture()));
        return new DrawCommand(selected, mesh, bindings, sortKey, 1, quadCount * INDICES_PER_QUAD);
    }

    private MeshHandle meshForFirstIndex(int firstIndex) {
        return meshesByFirstIndex.computeIfAbsent(firstIndex, index -> backend.createMesh(new MeshDescriptor(
                vertexBuffer, indexBuffer, index, quadCapacity * INDICES_PER_QUAD - index, IndexFormat.UINT32)));
    }

    PipelineHandle sharedPipeline() {
        return pipeline;
    }

    PipelineHandle sharedLitPipeline() {
        return litPipeline;
    }

    int light2dCount() {
        return light2dStorage.lightCount();
    }

    BindingSetHandle sharedLitBindings(TextureHandle albedo, TextureHandle normal,
                                       TextureHandle metallicRoughness, TextureHandle emissive) {
        return sharedLitBindings(albedo, normal, metallicRoughness, emissive, identityObject.handle());
    }

    BindingSetHandle sharedLitBindings(TextureHandle albedo, TextureHandle normal,
                                       TextureHandle metallicRoughness, TextureHandle emissive,
                                       BufferHandle objectUniform) {
        return litBindingsFor(new TextureSet(albedo,
                normal == null ? flatNormalTexture : normal,
                metallicRoughness == null ? whiteTexture : metallicRoughness,
                emissive == null ? blackTexture : emissive), objectUniform);
    }

    BindingSetHandle bindingsFor(TextureHandle texture) {
        return bindingsFor(texture, identityObject.handle());
    }

    BindingSetHandle bindingsFor(TextureHandle texture, BufferHandle objectUniform) {
        return bindingsByTexture.computeIfAbsent(new BindingKey(texture.id(), objectUniform.id()), key ->
                backend.createBindingSet(new BindingSetDescriptor(bindingLayout, List.of(
                        new Binding(0, UniformBufferBinding.whole(meshRenderSystem.frameUniformBuffer(),
                                MeshShaderBindings.FRAME_UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(texture)),
                        objectBinding(objectUniform)
                ))));
    }

    private BindingSetHandle litBindingsFor(TextureSet textures, BufferHandle objectUniform) {
        return litBindingsByTextures.computeIfAbsent(new LitBindingKey(textures, objectUniform.id()), key ->
                backend.createBindingSet(new BindingSetDescriptor(litBindingLayout, List.of(
                        new Binding(0, UniformBufferBinding.whole(meshRenderSystem.frameUniformBuffer(),
                                MeshShaderBindings.FRAME_UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(textures.albedo())),
                        new Binding(2, new SampledTextureBinding(textures.normal())),
                        new Binding(3, new SampledTextureBinding(textures.metallicRoughness())),
                        new Binding(4, new SampledTextureBinding(textures.emissive())),
                        objectBinding(objectUniform),
                        new Binding(6, StorageBufferBinding.whole(light2dStorage.handle(),
                                light2dStorage.byteSize()))
                ))));
    }

    private static Binding objectBinding(BufferHandle objectUniform) {
        return new Binding(Object2dUniform.BINDING,
                UniformBufferBinding.whole(objectUniform, Object2dUniform.BYTE_SIZE));
    }

    private record BindingKey(long textureId, long objectId) {
    }

    private record LitBindingKey(TextureSet textures, long objectId) {
    }

    private BindingSetHandle uniformBindingsFor(SpriteEntry entry, ParsedSource parsed) {
        SpriteRenderer sprite = entry.sprite();
        surfaceUniforms.writeIfNeeded(sprite, parsed);
        return uniformBindingsBySprite.computeIfAbsent(sprite, key -> {
            List<Binding> bindings = new ArrayList<>(baseBindingsOf(entry));
            surfaceUniforms.appendBindings(bindings, sprite, parsed);
            return backend.createBindingSet(new BindingSetDescriptor(
                    surfaceLayoutFor(new PipelineKey(sprite.surfaceShaderPath(), sprite.lit()), parsed), bindings));
        });
    }

    private List<Binding> baseBindingsOf(SpriteEntry entry) {
        Binding frameBinding = new Binding(0, UniformBufferBinding.whole(
                meshRenderSystem.frameUniformBuffer(), MeshShaderBindings.FRAME_UBO_SIZE));
        if (!entry.lit()) {
            return List.of(frameBinding, new Binding(1, new SampledTextureBinding(entry.texture())),
                    objectBinding(identityObject.handle()));
        }
        TextureSet textures = textureSetOf(entry);
        return List.of(frameBinding,
                new Binding(1, new SampledTextureBinding(textures.albedo())),
                new Binding(2, new SampledTextureBinding(textures.normal())),
                new Binding(3, new SampledTextureBinding(textures.metallicRoughness())),
                new Binding(4, new SampledTextureBinding(textures.emissive())),
                objectBinding(identityObject.handle()),
                new Binding(6, StorageBufferBinding.whole(light2dStorage.handle(), light2dStorage.byteSize())));
    }

    private void destroyMeshes() {
        meshesByFirstIndex.values().forEach(backend::destroy);
        meshesByFirstIndex.clear();
    }

    @Override
    public void shutdown(RenderBackend backend) {
        bindingsByTexture.values().forEach(backend::destroy);
        bindingsByTexture.clear();
        litBindingsByTextures.values().forEach(backend::destroy);
        litBindingsByTextures.clear();
        if (identityObject != null) {
            identityObject.destroy(backend);
            identityObject = null;
        }
        light2dStorage.shutdown();
        surfaceUniforms.shutdown();
        uniformBindingsBySprite.values().forEach(backend::destroy);
        uniformBindingsBySprite.clear();
        destroyMeshes();
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        backend.destroy(pipeline);
        backend.destroy(litPipeline);
        pipelinesByKey.values().forEach(backend::destroy);
        pipelinesByKey.clear();
    }
}
