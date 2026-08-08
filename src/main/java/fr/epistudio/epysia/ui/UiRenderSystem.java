package fr.epistudio.epysia.ui;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.gameobjects.GameObject;
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
import fr.epistudio.epysia.render.backend.RenderSurface;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.ScissorRect;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.text.Font;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import fr.epistudio.epysia.assets.AssetVariant;
import fr.epistudio.epysia.assets.LegacyAssetReferences;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class UiRenderSystem implements RenderSystem, UiPainter {
    private static final String VERTEX_PATH = "ui_image.vert.glsl";
    private static final String FRAGMENT_PATH = "ui_image.frag.glsl";
    private static final int UBO_SIZE = 32;
    private static final int INITIAL_QUAD_CAPACITY = 512;

    private final ShaderLoader shaderLoader;
    private final RenderSurface window;
    private final EpysiaEngine engine;
    private final UiDrawList drawList = new UiDrawList();
    private final UiClipStack clipStack = new UiClipStack();
    private final Map<UiShader, PipelineHandle> customPipelines = new HashMap<>();
    private final Map<TextureHandle, BindingSetHandle> bindingSets = new HashMap<>();
    private final Map<String, TextureHandle> texturesByPath = new HashMap<>();
    private final Set<String> missingTextures = new HashSet<>();
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(UBO_SIZE);
    private final long startNanos = System.nanoTime();

    private RenderBackend backend;
    private FontRegistry fontRegistry;
    private PipelineHandle defaultPipeline;
    private PipelineHandle activePipeline;
    private TextureHandle whiteTexture;
    private BufferHandle uiUbo;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private MeshHandle mesh;
    private int quadCapacity;
    private int collectCount;
    private int lastObjectCount;
    private int lastCanvasComponentCount;
    private int lastCanvasCount;
    private int lastElementCount;
    private int lastDrawCount;

    public UiRenderSystem(ShaderLoader shaderLoader, RenderSurface window, EpysiaEngine engine) {
        this.shaderLoader = shaderLoader;
        this.window = window;
        this.engine = engine;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        this.fontRegistry = engine.fonts();
        this.uiUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(UBO_SIZE)));
        this.defaultPipeline = createPipeline(VERTEX_PATH, FRAGMENT_PATH);
        this.whiteTexture = createWhiteTexture();
        allocateGeometry(INITIAL_QUAD_CAPACITY);
    }

    private TextureHandle createWhiteTexture() {
        TextureHandle handle = backend.createTexture(new TextureDescriptor(1, 1, TextureFormat.RGBA8,
                TextureUsage.SAMPLED, SamplerFilter.NEAREST));
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
        backend.writeTexture(handle, pixel);
        return handle;
    }

    private PipelineHandle createPipeline(String vertexPath, String fragmentPath) {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT2, 0);
        VertexAttribute uv = new VertexAttribute(1, VertexFormat.FLOAT2, 8);
        VertexAttribute color = new VertexAttribute(2, VertexFormat.FLOAT4, 16);
        VertexLayout vertexLayout = new VertexLayout(List.of(position, uv, color), UiDrawList.VERTEX_BYTES);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.DISABLED,
                BlendMode.ALPHA_BLEND, CullMode.NONE);
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        return backend.createPipeline(new PipelineDescriptor(
                new ShaderSource(vertex.source(), fragment.source()), vertexLayout, state, bindingLayout()));
    }

    private BindingSetLayout bindingLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D)));
    }

    private void allocateGeometry(int quads) {
        releaseGeometry();
        quadCapacity = quads;
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX,
                BufferUtils.createByteBuffer(quads * UiDrawList.VERTICES_PER_QUAD * UiDrawList.VERTEX_BYTES)));
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX,
                BufferUtils.createByteBuffer(quads * UiDrawList.INDICES_PER_QUAD * Integer.BYTES)));
        mesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0,
                quads * UiDrawList.INDICES_PER_QUAD, IndexFormat.UINT32));
    }

    private void releaseGeometry() {
        if (mesh == null) {
            return;
        }
        backend.destroy(mesh);
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        mesh = null;
    }

    private Font fontFor(UiFontStyle style) {
        return fontRegistry.resolve(engine.assets().locator(), style.path(), style.size());
    }

    private Optional<TextureHandle> textureFor(String path) {
        if (path.isEmpty()) {
            return Optional.empty();
        }
        if (missingTextures.contains(path)) {
            return Optional.empty();
        }
        TextureHandle cached = texturesByPath.get(path);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<TextureHandle> resolved = engine.assets().resolve(TextureHandle.class,
                LegacyAssetReferences.interpret(path, engine.assets()),
                AssetVariant.none());
        resolved.ifPresent(handle -> texturesByPath.put(path, handle));
        if (resolved.isEmpty()) {
            missingTextures.add(path);
        }
        return resolved;
    }

    @Override
    public UiImageSize imageSize(String texturePath) {
        return textureFor(texturePath)
                .map(handle -> new UiImageSize(backend.textureWidth(handle), backend.textureHeight(handle)))
                .orElseGet(UiImageSize::unknown);
    }

    @Override
    public void drawImage(UiRect rect, String texturePath, float uvMinX, float uvMinY,
                          float uvMaxX, float uvMaxY, UiColor tint) {
        textureFor(texturePath).ifPresent(handle ->
                drawTexture(rect, handle, uvMinX, uvMinY, uvMaxX, uvMaxY, tint));
    }

    @Override
    public float measureTextWidth(String text, UiFontStyle style) {
        return fontFor(style).measureWidth(text);
    }

    @Override
    public float lineHeight(UiFontStyle style) {
        return fontFor(style).pixelHeight();
    }

    public int collectCount() {
        return collectCount;
    }

    public int lastObjectCount() {
        return lastObjectCount;
    }

    public int lastCanvasComponentCount() {
        return lastCanvasComponentCount;
    }

    public int lastCanvasCount() {
        return lastCanvasCount;
    }

    public int lastElementCount() {
        return lastElementCount;
    }

    public int lastDrawCount() {
        return lastDrawCount;
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        collectCount++;
        lastObjectCount = 0;
        lastCanvasComponentCount = 0;
        lastCanvasCount = 0;
        lastElementCount = 0;
        lastDrawCount = 0;
        drawList.clear();
        clipStack.reset(new UiRect(0.0f, 0.0f, window.framebufferWidth(), window.framebufferHeight()));
        writeUbo();
        for (GameObject gameObject : scene.gameObjects()) {
            lastObjectCount++;
            if (!gameObject.active()) {
                continue;
            }
            gameObject.getComponent(UiCanvas.class).filter(UiCanvas::visible).ifPresent(this::collectCanvas);
        }
        submit(frame);
    }

    private void collectCanvas(UiCanvas canvas) {
        lastCanvasCount++;
        canvas.layout(window.framebufferWidth(), window.framebufferHeight());
        drawList.setScale(canvas.scaleFactor());
        clipStack.reset(canvas.viewport());
        clipStack.setScale(canvas.scaleFactor());
        activePipeline = defaultPipeline;
        for (UiElement root : canvas.roots()) {
            paintElement(root);
        }
    }

    private void paintElement(UiElement element) {
        if (!element.drawable()) {
            return;
        }
        lastElementCount++;
        element.paintInto(this);
        boolean clipped = element.clipChildren() && clipStack.push(element.computedRect());
        for (UiElement child : element.children()) {
            paintElement(child);
        }
        if (clipped) {
            clipStack.pop();
        }
    }

    @Override
    public void fillRect(UiRect rect, UiColor color) {
        if (color.alpha() <= 0.0f) {
            return;
        }
        drawList.setState(activePipeline, whiteTexture, clipStack.current());
        drawList.addQuad(rect, 0.0f, 0.0f, 1.0f, 1.0f, color);
    }

    @Override
    public void drawTexture(UiRect rect, TextureHandle texture, float uvMinX, float uvMinY,
                            float uvMaxX, float uvMaxY, UiColor tint) {
        if (texture == null || tint.alpha() <= 0.0f) {
            return;
        }
        drawList.setState(activePipeline, texture, clipStack.current());
        drawList.addQuad(rect, uvMinX, uvMinY, uvMaxX, uvMaxY, tint);
    }

    @Override
    public void drawText(float x, float y, String text, UiFontStyle style, UiColor color) {
        if (color.alpha() <= 0.0f) {
            return;
        }
        Font font = fontFor(style);
        drawList.setState(activePipeline, font.atlasTexture(), clipStack.current());
        UiTextShaper.appendText(drawList, font, text, x, y, color);
    }

    private void writeUbo() {
        uboScratch.clear();
        float timeSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0f;
        uboScratch.putFloat(window.framebufferWidth()).putFloat(window.framebufferHeight())
                .putFloat(0.0f).putFloat(0.0f);
        uboScratch.putFloat(timeSeconds).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        uboScratch.flip();
        backend.writeBuffer(uiUbo, uboScratch, 0L);
    }

    private void submit(FrameBuilder frame) {
        if (drawList.isEmpty()) {
            return;
        }
        uploadGeometry();
        long order = 1L;
        for (UiDrawList.Command command : drawList.commands()) {
            if (command.indexCount() == 0) {
                continue;
            }
            frame.submit(RenderPasses.UI, DrawCommand.range(command.pipeline(), mesh,
                    bindingSetFor(command.texture()), order, command.firstIndex(),
                    command.indexCount(), command.scissor()));
            lastDrawCount++;
            order++;
        }
    }

    private void uploadGeometry() {
        ByteBuffer vertexData = drawList.vertexData();
        ByteBuffer indexData = drawList.indexData();
        int requiredQuads = indexData.remaining() / (UiDrawList.INDICES_PER_QUAD * Integer.BYTES);
        if (requiredQuads > quadCapacity) {
            allocateGeometry(Math.max(requiredQuads, quadCapacity * 2));
        }
        backend.writeBuffer(vertexBuffer, vertexData, 0L);
        backend.writeBuffer(indexBuffer, indexData, 0L);
    }

    private BindingSetHandle bindingSetFor(TextureHandle texture) {
        return bindingSets.computeIfAbsent(texture, handle -> backend.createBindingSet(
                new BindingSetDescriptor(bindingLayout(), List.of(
                        new Binding(0, UniformBufferBinding.whole(uiUbo, UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(handle))))));
    }

    @Override
    public void shutdown(RenderBackend backend) {
        for (BindingSetHandle bindings : bindingSets.values()) {
            backend.destroy(bindings);
        }
        bindingSets.clear();
        for (PipelineHandle pipeline : customPipelines.values()) {
            backend.destroy(pipeline);
        }
        customPipelines.clear();
        releaseGeometry();
        backend.destroy(whiteTexture);
        backend.destroy(defaultPipeline);
        backend.destroy(uiUbo);
    }
}
