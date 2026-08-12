package fr.epistudio.epysia.render.debug;

import fr.epistudio.epysia.debug.DebugDraw;
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
import fr.epistudio.epysia.render.backend.ScissorRect;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.Topology;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

public final class DebugLineRenderSystem implements RenderSystem {

    private static final String VERTEX_PATH = "debug_line.vert.glsl";
    private static final String FRAGMENT_PATH = "debug_line.frag.glsl";
    private static final int MAX_SEGMENTS = 16384;
    private static final int VERTICES_PER_SEGMENT = 2;
    private static final int VERTEX_BYTES = 28;
    private static final int FLOATS_PER_SEGMENT = 6;
    private static final int FRAME_UBO_BINDING = 0;
    private static final float COLOR_SCALE = 1.0f / 255.0f;

    private final ShaderLoader shaderLoader;
    private final MeshRenderSystem meshRenderSystem;
    private final DebugDraw debugDraw;

    private RenderBackend backend;
    private PipelineHandle pipeline;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private MeshHandle mesh;
    private BindingSetHandle bindings;

    private final ByteBuffer vertexScratch =
            BufferUtils.createByteBuffer(MAX_SEGMENTS * VERTICES_PER_SEGMENT * VERTEX_BYTES);

    public DebugLineRenderSystem(ShaderLoader shaderLoader, MeshRenderSystem meshRenderSystem,
                                 DebugDraw debugDraw) {
        this.shaderLoader = shaderLoader;
        this.meshRenderSystem = meshRenderSystem;
        this.debugDraw = debugDraw;
    }

    @Override
    public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
        this.backend = renderBackend;
        BindingSetLayout layout = new BindingSetLayout(List.of(
                new BindingSlot(FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER)));
        pipeline = createPipeline(layout);
        allocateMeshBuffers();
        bindings = renderBackend.createBindingSet(new BindingSetDescriptor(layout, List.of(
                new Binding(FRAME_UBO_BINDING, UniformBufferBinding.whole(
                        meshRenderSystem.frameUniformBuffer(), MeshShaderBindings.FRAME_UBO_SIZE)))));
    }

    private PipelineHandle createPipeline(BindingSetLayout layout) {
        VertexLayout vertexLayout = new VertexLayout(List.of(
                new VertexAttribute(0, VertexFormat.FLOAT3, 0),
                new VertexAttribute(1, VertexFormat.FLOAT4, 12)), VERTEX_BYTES);
        RenderState state = new RenderState(Topology.LINES, DepthTest.LESS_EQUAL,
                BlendMode.ALPHA_BLEND, CullMode.NONE);
        ShaderSource source = new ShaderSource(shaderLoader.load(VERTEX_PATH).source(),
                shaderLoader.load(FRAGMENT_PATH).source());
        return backend.createPipeline(new PipelineDescriptor(source, vertexLayout, state, layout));
    }

    private void allocateMeshBuffers() {
        int vertexCount = MAX_SEGMENTS * VERTICES_PER_SEGMENT;
        ByteBuffer vertexInit = BufferUtils.createByteBuffer(vertexCount * VERTEX_BYTES);
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertexInit));
        ByteBuffer indexInit = BufferUtils.createByteBuffer(vertexCount * Integer.BYTES);
        for (int index = 0; index < vertexCount; index++) {
            indexInit.putInt(index);
        }
        indexInit.flip();
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, indexInit));
        mesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0,
                vertexCount, IndexFormat.UINT32));
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        int segments = Math.min(debugDraw.segmentCount(), MAX_SEGMENTS);
        if (segments <= 0 || context.primaryCamera().isEmpty()) {
            return;
        }
        vertexScratch.clear();
        appendSegments(segments);
        vertexScratch.flip();
        backend.writeBuffer(vertexBuffer, vertexScratch, 0L);
        frame.submit(RenderPasses.TRANSPARENT_3D, DrawCommand.range(pipeline, mesh, bindings,
                0L, 0, segments * VERTICES_PER_SEGMENT, ScissorRect.disabled()));
    }

    private void appendSegments(int segments) {
        float[] endpoints = debugDraw.endpoints();
        int[] colors = debugDraw.colors();
        for (int index = 0; index < segments; index++) {
            int base = index * FLOATS_PER_SEGMENT;
            appendVertex(endpoints[base], endpoints[base + 1], endpoints[base + 2], colors[index]);
            appendVertex(endpoints[base + 3], endpoints[base + 4], endpoints[base + 5], colors[index]);
        }
    }

    private void appendVertex(float x, float y, float z, int color) {
        vertexScratch.putFloat(x).putFloat(y).putFloat(z);
        vertexScratch.putFloat(((color >> 16) & 0xFF) * COLOR_SCALE);
        vertexScratch.putFloat(((color >> 8) & 0xFF) * COLOR_SCALE);
        vertexScratch.putFloat((color & 0xFF) * COLOR_SCALE);
        vertexScratch.putFloat(alphaOf(color));
    }

    private static float alphaOf(int color) {
        int alpha = (color >>> 24) & 0xFF;
        return alpha == 0 ? 1.0f : alpha * COLOR_SCALE;
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        renderBackend.destroy(bindings);
        renderBackend.destroy(mesh);
        renderBackend.destroy(vertexBuffer);
        renderBackend.destroy(indexBuffer);
        renderBackend.destroy(pipeline);
    }
}
