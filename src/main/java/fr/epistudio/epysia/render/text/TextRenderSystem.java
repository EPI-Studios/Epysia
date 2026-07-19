package fr.epistudio.epysia.render.text;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.Stage;
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
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.DefaultHud;
import fr.epistudio.epysia.render.backend.RenderSurface;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

public final class TextRenderSystem implements RenderSystem {

    private static final String VERTEX_PATH = "text.vert.glsl";
    private static final String FRAGMENT_PATH = "text.frag.glsl";
    private static final String FONT_RESOURCE = "fonts/AdwaitaMono-Regular.ttf";
    private static final float FONT_PIXEL_HEIGHT = 32.0f;
    private static final int MAX_QUADS = 1024;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;
    private static final int VERTEX_BYTES = 16;
    private static final int UBO_SIZE = 32;

    private final ShaderLoader shaderLoader;
    private final RenderSurface window;
    private final Logger logger;
    private final EpysiaEngine engine;

    private RenderBackend backend;
    private Font font;
    private PipelineHandle pipeline;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private BufferHandle textUbo;
    private MeshHandle mesh;
    private BindingSetHandle bindings;
    private final ByteBuffer vertexScratch = BufferUtils.createByteBuffer(MAX_QUADS * VERTICES_PER_QUAD * VERTEX_BYTES);
    private final ByteBuffer indexScratch = BufferUtils.createByteBuffer(MAX_QUADS * INDICES_PER_QUAD * Integer.BYTES);
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(UBO_SIZE);

    public TextRenderSystem(ShaderLoader shaderLoader, RenderSurface window, EpysiaEngine engine, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.window = window;
        this.engine = engine;
        this.logger = logger;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        font = Font.loadFromResource(backend, FONT_RESOURCE, FONT_PIXEL_HEIGHT);
        BindingSetLayout layout = new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.UNIFORM_BUFFER),
                new BindingSlot(1, BindingType.SAMPLED_TEXTURE_2D)
        ));
        pipeline = backend.createPipeline(buildPipelineDescriptor(layout));
        allocateMeshBuffers();
        createTextUbo();
        bindings = backend.createBindingSet(new BindingSetDescriptor(layout,
                List.of(
                        new Binding(0, UniformBufferBinding.whole(textUbo, UBO_SIZE)),
                        new Binding(1, new SampledTextureBinding(font.atlasTexture()))
                )));
    }

    private PipelineDescriptor buildPipelineDescriptor(BindingSetLayout layout) {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT2, 0);
        VertexAttribute uv = new VertexAttribute(1, VertexFormat.FLOAT2, 8);
        VertexLayout vertexLayout = new VertexLayout(List.of(position, uv), VERTEX_BYTES);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.ALPHA_BLEND, CullMode.NONE);
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        return new PipelineDescriptor(new ShaderSource(vertex.source(), fragment.source()), vertexLayout, state, layout);
    }

    private void allocateMeshBuffers() {
        ByteBuffer vertexInit = BufferUtils.createByteBuffer(MAX_QUADS * VERTICES_PER_QUAD * VERTEX_BYTES);
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertexInit));
        ByteBuffer indexInit = BufferUtils.createByteBuffer(MAX_QUADS * INDICES_PER_QUAD * Integer.BYTES);
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, indexInit));
        mesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0, MAX_QUADS * INDICES_PER_QUAD, IndexFormat.UINT32));
    }

    private void createTextUbo() {
        ByteBuffer initial = BufferUtils.createByteBuffer(UBO_SIZE);
        textUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, initial));
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        vertexScratch.clear();
        indexScratch.clear();
        int quadCount = 0;
        for (DefaultHud.Entry entry : engine.hudEntries().entries()) {
            quadCount = appendText(entry.message(), entry.x(), entry.y(), quadCount);
        }
        if (quadCount <= 0) {
            return;
        }
        vertexScratch.flip();
        indexScratch.flip();
        backend.writeBuffer(vertexBuffer, vertexScratch, 0L);
        backend.writeBuffer(indexBuffer, indexScratch, 0L);
        writeUbo();
        frame.submit(Stage.UI, DrawCommand.withIndexCount(pipeline, mesh, bindings, quadCount * INDICES_PER_QUAD));
    }

    private int appendText(String text, float startX, float startY, int quadCursor) {
        int quadCount = quadCursor;
        float lineHeight = font.pixelHeight() * 1.2f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xPosition = stack.floats(startX);
            FloatBuffer yPosition = stack.floats(startY + font.pixelHeight());
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            for (int i = 0; i < text.length() && quadCount < MAX_QUADS; i++) {
                char character = text.charAt(i);
                if (character == '\n') {
                    xPosition.put(0, startX);
                    yPosition.put(0, yPosition.get(0) + lineHeight);
                    continue;
                }
                if (character < Font.FIRST_CHAR || character >= Font.FIRST_CHAR + Font.CHAR_COUNT) {
                    continue;
                }
                STBTruetype.stbtt_GetBakedQuad(font.characterData(), Font.ATLAS_SIZE, Font.ATLAS_SIZE,
                        character - Font.FIRST_CHAR, xPosition, yPosition, quad, true);
                appendQuad(quad, quadCount);
                quadCount++;
            }
        }
        return quadCount;
    }

    private void appendQuad(STBTTAlignedQuad quad, int quadIndex) {
        float x0 = quad.x0(), y0 = quad.y0(), x1 = quad.x1(), y1 = quad.y1();
        float s0 = quad.s0(), t0 = quad.t0(), s1 = quad.s1(), t1 = quad.t1();
        vertexScratch.putFloat(x0).putFloat(y0).putFloat(s0).putFloat(t0);
        vertexScratch.putFloat(x1).putFloat(y0).putFloat(s1).putFloat(t0);
        vertexScratch.putFloat(x1).putFloat(y1).putFloat(s1).putFloat(t1);
        vertexScratch.putFloat(x0).putFloat(y1).putFloat(s0).putFloat(t1);
        int base = quadIndex * VERTICES_PER_QUAD;
        indexScratch.putInt(base).putInt(base + 1).putInt(base + 2);
        indexScratch.putInt(base).putInt(base + 2).putInt(base + 3);
    }

    private void writeUbo() {
        uboScratch.clear();
        uboScratch.putFloat(window.framebufferWidth()).putFloat(window.framebufferHeight()).putFloat(0.0f).putFloat(0.0f);
        uboScratch.putFloat(1.0f).putFloat(1.0f).putFloat(1.0f).putFloat(1.0f);
        uboScratch.flip();
        backend.writeBuffer(textUbo, uboScratch, 0L);
    }

    @Override
    public void shutdown(RenderBackend backend) {
        backend.destroy(bindings);
        backend.destroy(textUbo);
        backend.destroy(mesh);
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        backend.destroy(pipeline);
        font.destroy(backend);
    }
}
