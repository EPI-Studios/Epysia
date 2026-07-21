package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BlendMode;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.CullMode;
import fr.epistudio.epysia.render.backend.DepthTest;
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
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.text.Font;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.render.backend.RenderSurface;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class UiRenderSystem implements RenderSystem {

    private static final int PANEL_VERTEX_BYTES = 24;
    private static final int TEXTURED_VERTEX_BYTES = 32;
    private static final int MAX_QUADS_PER_BATCH = 1024;
    private static final int UBO_SIZE = 32;

    private final ShaderLoader shaderLoader;
    private final RenderSurface window;
    private final EpysiaEngine engine;
    private RenderBackend backend;
    private FontRegistry fontRegistry;
    private PipelineHandle defaultPanelPipeline;
    private PipelineHandle defaultTextPipeline;
    private PipelineHandle defaultImagePipeline;
    private BufferHandle uiUbo;
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(UBO_SIZE);
    private final Map<UiShader, PipelineHandle> customPipelines = new HashMap<>();
    private final Map<UiBatchKey, UiQuadBatch> batches = new HashMap<>();
    private final List<UiQuadBatch> activeBatches = new ArrayList<>();
    private final long startNanos = System.nanoTime();

    public UiRenderSystem(ShaderLoader shaderLoader, RenderSurface window, EpysiaEngine engine) {
        this.shaderLoader = shaderLoader;
        this.window = window;
        this.engine = engine;
    }

    public Font defaultFont() {
        return fontRegistry.get(FontRegistry.DEFAULT_NAME);
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.fontRegistry = engine.fonts();
        createUbo();
        defaultPanelPipeline = createPipeline("ui_panel.vert.glsl", "ui_panel.frag.glsl", UiShaderKind.PANEL);
        defaultTextPipeline = createPipeline("ui_text.vert.glsl", "ui_text.frag.glsl", UiShaderKind.IMAGE);
        defaultImagePipeline = createPipeline("ui_image.vert.glsl", "ui_image.frag.glsl", UiShaderKind.IMAGE);
    }

    private void createUbo() {
        ByteBuffer initial = BufferUtils.createByteBuffer(UBO_SIZE);
        uiUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, initial));
    }

    private PipelineHandle createPipeline(String vertexPath, String fragmentPath, UiShaderKind kind) {
        BindingSetLayout layout = pipelineLayout(kind);
        VertexLayout vertexLayout = vertexLayout(kind);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.ALPHA_BLEND, CullMode.NONE);
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        return backend.createPipeline(new PipelineDescriptor(new ShaderSource(vertex.source(), fragment.source()), vertexLayout, state, layout));
    }

    private BindingSetLayout pipelineLayout(UiShaderKind kind) {
        if (kind == UiShaderKind.PANEL) {
            return new BindingSetLayout(List.of(new BindingSlot(0, BindingType.UNIFORM_BUFFER)));
        }
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D)
        ));
    }

    private VertexLayout vertexLayout(UiShaderKind kind) {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT2, 0);
        if (kind == UiShaderKind.PANEL) {
            VertexAttribute color = new VertexAttribute(1, VertexFormat.FLOAT4, 8);
            return new VertexLayout(List.of(position, color), PANEL_VERTEX_BYTES);
        }
        VertexAttribute uv = new VertexAttribute(1, VertexFormat.FLOAT2, 8);
        VertexAttribute color = new VertexAttribute(2, VertexFormat.FLOAT4, 16);
        return new VertexLayout(List.of(position, uv, color), TEXTURED_VERTEX_BYTES);
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        resetBatches();
        writeUbo();
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(UiCanvasComponent.class).ifPresent(this::collectCanvas);
        }
        submitBatches(frame);
    }

    private void resetBatches() {
        for (UiQuadBatch batch : activeBatches) {
            batch.reset();
        }
        activeBatches.clear();
    }

    private void writeUbo() {
        uboScratch.clear();
        float timeSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
        uboScratch.putFloat(window.framebufferWidth()).putFloat(window.framebufferHeight()).putFloat(0.0f).putFloat(0.0f);
        uboScratch.putFloat(timeSeconds).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        uboScratch.flip();
        backend.writeBuffer(uiUbo, uboScratch, 0L);
    }

    private void collectCanvas(UiCanvasComponent canvas) {
        canvas.resizeToViewport(window.framebufferWidth(), window.framebufferHeight());
        walkNode(canvas.root());
    }

    private void walkNode(UiNode node) {
        if (!node.visible()) {
            return;
        }
        emitNode(node);
        for (UiNode child : node.children()) {
            walkNode(child);
        }
    }

    private void emitNode(UiNode node) {
        switch (node) {
            case UiPanel panel -> emitPanel(panel.computedRect(), panel.color(), panel.customShader().orElse(null));
            case UiButton button -> emitPanel(button.computedRect(), button.currentColor(), button.customShader().orElse(null));
            case UiLabel label -> emitLabel(label);
            case UiImage image -> emitImage(image);
            case UiStack stack -> { }
        }
    }

    private void emitPanel(UiRect rect, UiColor color, UiShader shader) {
        if (color.alpha() <= 0.0f) {
            return;
        }
        PipelineHandle pipeline = shader == null ? defaultPanelPipeline : resolveCustomPipeline(shader, UiShaderKind.PANEL);
        UiBatchKey key = new UiBatchKey(pipeline, null, UiShaderKind.PANEL);
        UiQuadBatch batch = acquireBatch(key);
        if (batch.isFull()) {
            return;
        }
        appendPanelQuad(batch, rect, color);
    }

    private void appendPanelQuad(UiQuadBatch batch, UiRect rect, UiColor color) {
        float x0 = rect.x(), y0 = rect.y(), x1 = x0 + rect.width(), y1 = y0 + rect.height();
        appendPanelVertex(batch, x0, y0, color);
        appendPanelVertex(batch, x1, y0, color);
        appendPanelVertex(batch, x1, y1, color);
        appendPanelVertex(batch, x0, y1, color);
        emitIndices(batch);
    }

    private void appendPanelVertex(UiQuadBatch batch, float x, float y, UiColor color) {
        batch.vertexScratch.putFloat(x).putFloat(y)
                .putFloat(color.red()).putFloat(color.green()).putFloat(color.blue()).putFloat(color.alpha());
    }

    private void emitImage(UiImage image) {
        if (image.texture() == null) {
            return;
        }
        PipelineHandle pipeline = image.customShader()
                .map(shader -> resolveCustomPipeline(shader, UiShaderKind.IMAGE))
                .orElse(defaultImagePipeline);
        UiBatchKey key = new UiBatchKey(pipeline, image.texture(), UiShaderKind.IMAGE);
        UiQuadBatch batch = acquireBatch(key);
        if (batch.isFull()) {
            return;
        }
        appendTexturedQuad(batch, image.computedRect(), image.tint(),
                image.uvMinX(), image.uvMinY(), image.uvMaxX(), image.uvMaxY());
    }

    private void emitLabel(UiLabel label) {
        Font font = label.font() != null ? label.font() : fontRegistry.get(FontRegistry.DEFAULT_NAME);
        UiBatchKey key = new UiBatchKey(defaultTextPipeline, font.atlasTexture(), UiShaderKind.IMAGE);
        UiQuadBatch batch = acquireBatch(key);
        UiRect rect = label.computedRect();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xPosition = stack.floats(rect.x());
            FloatBuffer yPosition = stack.floats(rect.y() + font.pixelHeight());
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            for (int i = 0; i < label.text().length(); i++) {
                appendLabelCharacter(batch, font, label, label.text().charAt(i), quad, xPosition, yPosition, rect.x());
            }
        }
    }

    private void appendLabelCharacter(UiQuadBatch batch, Font font, UiLabel label, char character,
                                       STBTTAlignedQuad quad, FloatBuffer xPosition, FloatBuffer yPosition, float startX) {
        if (character == '\n') {
            xPosition.put(0, startX);
            yPosition.put(0, yPosition.get(0) + font.pixelHeight() * 1.2f);
            return;
        }
        if (character < Font.FIRST_CHAR || character >= Font.FIRST_CHAR + Font.CHAR_COUNT) {
            return;
        }
        if (batch.isFull()) {
            return;
        }
        STBTruetype.stbtt_GetBakedQuad(font.characterData(), Font.ATLAS_SIZE, Font.ATLAS_SIZE,
                character - Font.FIRST_CHAR, xPosition, yPosition, quad, true);
        appendTexturedQuadFromBakedQuad(batch, quad, label.color());
    }

    private void appendTexturedQuadFromBakedQuad(UiQuadBatch batch, STBTTAlignedQuad quad, UiColor color) {
        float x0 = quad.x0(), y0 = quad.y0(), x1 = quad.x1(), y1 = quad.y1();
        float s0 = quad.s0(), t0 = quad.t0(), s1 = quad.s1(), t1 = quad.t1();
        appendTexturedVertex(batch, x0, y0, s0, t0, color);
        appendTexturedVertex(batch, x1, y0, s1, t0, color);
        appendTexturedVertex(batch, x1, y1, s1, t1, color);
        appendTexturedVertex(batch, x0, y1, s0, t1, color);
        emitIndices(batch);
    }

    private void appendTexturedQuad(UiQuadBatch batch, UiRect rect, UiColor color,
                                     float uvMinX, float uvMinY, float uvMaxX, float uvMaxY) {
        float x0 = rect.x(), y0 = rect.y(), x1 = x0 + rect.width(), y1 = y0 + rect.height();
        appendTexturedVertex(batch, x0, y0, uvMinX, uvMinY, color);
        appendTexturedVertex(batch, x1, y0, uvMaxX, uvMinY, color);
        appendTexturedVertex(batch, x1, y1, uvMaxX, uvMaxY, color);
        appendTexturedVertex(batch, x0, y1, uvMinX, uvMaxY, color);
        emitIndices(batch);
    }

    private void appendTexturedVertex(UiQuadBatch batch, float x, float y, float u, float v, UiColor color) {
        batch.vertexScratch.putFloat(x).putFloat(y).putFloat(u).putFloat(v)
                .putFloat(color.red()).putFloat(color.green()).putFloat(color.blue()).putFloat(color.alpha());
    }

    private void emitIndices(UiQuadBatch batch) {
        int base = batch.quadCount * UiQuadBatch.VERTICES_PER_QUAD;
        batch.indexScratch.putInt(base).putInt(base + 1).putInt(base + 2);
        batch.indexScratch.putInt(base).putInt(base + 2).putInt(base + 3);
        batch.quadCount++;
    }

    private PipelineHandle resolveCustomPipeline(UiShader shader, UiShaderKind requiredKind) {
        if (shader.kind() != requiredKind) {
            return requiredKind == UiShaderKind.PANEL ? defaultPanelPipeline : defaultImagePipeline;
        }
        return customPipelines.computeIfAbsent(shader,
                key -> createPipeline(key.vertexPath(), key.fragmentPath(), key.kind()));
    }

    private UiQuadBatch acquireBatch(UiBatchKey key) {
        UiQuadBatch existing = batches.get(key);
        if (existing != null) {
            if (!activeBatches.contains(existing)) {
                activeBatches.add(existing);
            }
            return existing;
        }
        UiQuadBatch created = createBatch(key);
        batches.put(key, created);
        activeBatches.add(created);
        return created;
    }

    private UiQuadBatch createBatch(UiBatchKey key) {
        int vertexBytes = key.kind() == UiShaderKind.PANEL ? PANEL_VERTEX_BYTES : TEXTURED_VERTEX_BYTES;
        ByteBuffer vertexInit = BufferUtils.createByteBuffer(MAX_QUADS_PER_BATCH * UiQuadBatch.VERTICES_PER_QUAD * vertexBytes);
        BufferHandle vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertexInit));
        ByteBuffer indexInit = BufferUtils.createByteBuffer(MAX_QUADS_PER_BATCH * UiQuadBatch.INDICES_PER_QUAD * Integer.BYTES);
        BufferHandle indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, indexInit));
        MeshHandle mesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0, MAX_QUADS_PER_BATCH * UiQuadBatch.INDICES_PER_QUAD, IndexFormat.UINT32));
        BindingSetHandle bindings = createBindingSet(key);
        ByteBuffer vertexScratch = BufferUtils.createByteBuffer(MAX_QUADS_PER_BATCH * UiQuadBatch.VERTICES_PER_QUAD * vertexBytes);
        ByteBuffer indexScratch = BufferUtils.createByteBuffer(MAX_QUADS_PER_BATCH * UiQuadBatch.INDICES_PER_QUAD * Integer.BYTES);
        return new UiQuadBatch(key.pipeline(), bindings, vertexBuffer, indexBuffer, mesh, vertexScratch, indexScratch, vertexBytes, MAX_QUADS_PER_BATCH);
    }

    private BindingSetHandle createBindingSet(UiBatchKey key) {
        BindingSetLayout layout = pipelineLayout(key.kind());
        if (key.kind() == UiShaderKind.PANEL) {
            return backend.createBindingSet(new BindingSetDescriptor(layout,
                    List.of(new Binding(0, UniformBufferBinding.whole(uiUbo, UBO_SIZE)))));
        }
        TextureHandle texture = key.texture();
        return backend.createBindingSet(new BindingSetDescriptor(layout,
                List.of(
                        new Binding(0, UniformBufferBinding.whole(uiUbo, UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(texture))
                )));
    }

    private void submitBatches(FrameBuilder frame) {
        for (UiQuadBatch batch : activeBatches) {
            if (batch.quadCount == 0) {
                continue;
            }
            batch.vertexScratch.flip();
            batch.indexScratch.flip();
            backend.writeBuffer(batch.vertexBuffer, batch.vertexScratch, 0L);
            backend.writeBuffer(batch.indexBuffer, batch.indexScratch, 0L);
            frame.submit(RenderPasses.UI, DrawCommand.withIndexCount(batch.pipeline, batch.mesh, batch.bindings, batch.indexCount()));
        }
    }

    @Override
    public void shutdown(RenderBackend backend) {
        for (UiQuadBatch batch : batches.values()) {
            backend.destroy(batch.bindings);
            backend.destroy(batch.mesh);
            backend.destroy(batch.vertexBuffer);
            backend.destroy(batch.indexBuffer);
        }
        batches.clear();
        for (PipelineHandle pipeline : customPipelines.values()) {
            backend.destroy(pipeline);
        }
        customPipelines.clear();
        backend.destroy(defaultPanelPipeline);
        backend.destroy(defaultTextPipeline);
        backend.destroy(defaultImagePipeline);
        backend.destroy(uiUbo);
    }
}
