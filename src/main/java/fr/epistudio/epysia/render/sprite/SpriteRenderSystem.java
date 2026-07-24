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
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SpriteRenderSystem implements RenderSystem {

    private static final String VERTEX_PATH = "sprite.vert.glsl";
    private static final String FRAGMENT_PATH = "sprite.frag.glsl";
    static final int VERTICES_PER_QUAD = 4;
    static final int INDICES_PER_QUAD = 6;
    static final int VERTEX_BYTES = 32;
    private static final int INITIAL_QUAD_CAPACITY = 1024;

    private record SpriteEntry(Transform2D transform, SpriteRenderer sprite, TextureHandle texture) {
    }

    private static final Comparator<SpriteEntry> PAINTER_ORDER = Comparator
            .comparingInt((SpriteEntry entry) -> entry.sprite().sortingLayer())
            .thenComparingInt(entry -> entry.sprite().orderInLayer())
            .thenComparingInt(entry -> entry.transform().renderLayer());

    private final ShaderLoader shaderLoader;
    private final MeshRenderSystem meshRenderSystem;
    private final Logger logger;
    private final List<SpriteEntry> entries = new ArrayList<>();
    private final Map<Long, BindingSetHandle> bindingsByTexture = new HashMap<>();
    private final Map<Integer, MeshHandle> meshesByFirstIndex = new HashMap<>();
    private final Vector2f scratchCorner = new Vector2f();

    private RenderBackend backend;
    private BindingSetLayout bindingLayout;
    private PipelineHandle pipeline;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private ByteBuffer vertexScratch;
    private int quadCapacity;

    public SpriteRenderSystem(ShaderLoader shaderLoader, MeshRenderSystem meshRenderSystem, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.meshRenderSystem = meshRenderSystem;
        this.logger = logger;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        bindingLayout = new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D)
        ));
        pipeline = backend.createPipeline(buildPipelineDescriptor());
        allocateBuffers(INITIAL_QUAD_CAPACITY);
    }

    private PipelineDescriptor buildPipelineDescriptor() {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT2, 0);
        VertexAttribute uv = new VertexAttribute(1, VertexFormat.FLOAT2, 8);
        VertexAttribute color = new VertexAttribute(2, VertexFormat.FLOAT4, 16);
        VertexLayout vertexLayout = new VertexLayout(List.of(position, uv, color), VERTEX_BYTES);
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        return new PipelineDescriptor(new ShaderSource(vertex.source(), fragment.source()),
                vertexLayout, RenderState.SPRITE_2D, bindingLayout);
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
        for (GameObject gameObject : scene.gameObjects()) {
            Transform2D transform = gameObject.getComponentOrNull(Transform2D.class);
            SpriteRenderer sprite = gameObject.getComponentOrNull(SpriteRenderer.class);
            if (transform == null || sprite == null || !transform.visible()) {
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
        Matrix3x2f matrix = entry.transform().localMatrix();
        appendVertex(matrix, -halfWidth, -halfHeight, leftU, bottomV, sprite);
        appendVertex(matrix, halfWidth, -halfHeight, rightU, bottomV, sprite);
        appendVertex(matrix, halfWidth, halfHeight, rightU, topV, sprite);
        appendVertex(matrix, -halfWidth, halfHeight, leftU, topV, sprite);
    }

    private void appendVertex(Matrix3x2f matrix, float cornerX, float cornerY, float u, float v, SpriteRenderer sprite) {
        scratchCorner.set(cornerX, cornerY);
        matrix.transformPosition(scratchCorner);
        Vector3f tint = sprite.tint();
        vertexScratch.putFloat(scratchCorner.x).putFloat(scratchCorner.y);
        vertexScratch.putFloat(u).putFloat(v);
        vertexScratch.putFloat(tint.x).putFloat(tint.y).putFloat(tint.z).putFloat(sprite.opacity());
    }

    private void submitBatches(FrameBuilder frame) {
        int batchStart = 0;
        long sequence = 0L;
        for (int i = 1; i <= entries.size(); i++) {
            if (i < entries.size() && entries.get(i).texture().id() == entries.get(batchStart).texture().id()) {
                continue;
            }
            frame.submit(RenderPasses.WORLD_2D, batchCommand(batchStart, i - batchStart, sequence));
            sequence++;
            batchStart = i;
        }
    }

    private DrawCommand batchCommand(int firstQuad, int quadCount, long sequence) {
        MeshHandle mesh = meshForFirstIndex(firstQuad * INDICES_PER_QUAD);
        SpriteRenderer sprite = entries.get(firstQuad).sprite();
        long sortKey = SpriteSortKeys.compose(sprite.sortingLayer(), sprite.orderInLayer(), sequence);
        BindingSetHandle bindings = bindingsFor(entries.get(firstQuad).texture());
        return new DrawCommand(pipeline, mesh, bindings, sortKey, 1, quadCount * INDICES_PER_QUAD);
    }

    private MeshHandle meshForFirstIndex(int firstIndex) {
        return meshesByFirstIndex.computeIfAbsent(firstIndex, index -> backend.createMesh(new MeshDescriptor(
                vertexBuffer, indexBuffer, index, quadCapacity * INDICES_PER_QUAD - index, IndexFormat.UINT32)));
    }

    PipelineHandle sharedPipeline() {
        return pipeline;
    }

    BindingSetHandle bindingsFor(TextureHandle texture) {
        return bindingsByTexture.computeIfAbsent(texture.id(), id ->
                backend.createBindingSet(new BindingSetDescriptor(bindingLayout, List.of(
                        new Binding(0, UniformBufferBinding.whole(meshRenderSystem.frameUniformBuffer(),
                                MeshShaderBindings.FRAME_UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(texture))
                ))));
    }

    private void destroyMeshes() {
        meshesByFirstIndex.values().forEach(backend::destroy);
        meshesByFirstIndex.clear();
    }

    @Override
    public void shutdown(RenderBackend backend) {
        bindingsByTexture.values().forEach(backend::destroy);
        bindingsByTexture.clear();
        destroyMeshes();
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        backend.destroy(pipeline);
    }
}
