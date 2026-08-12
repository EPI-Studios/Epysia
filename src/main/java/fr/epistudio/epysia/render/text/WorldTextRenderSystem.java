package fr.epistudio.epysia.render.text;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.WorldText;
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
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.SamplerFilter;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

public final class WorldTextRenderSystem implements RenderSystem {

    private static final String VERTEX_PATH = "world_text.vert.glsl";
    private static final String FRAGMENT_PATH = "world_text.frag.glsl";
    private static final String FONT_RESOURCE = "fonts/AdwaitaMono-Regular.ttf";
    private static final float FONT_PIXEL_HEIGHT = 48.0f;
    private static final int MAX_QUADS = 2048;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;
    private static final int VERTEX_BYTES = 40;
    private static final int FRAME_UBO_BINDING = 0;
    private static final int ATLAS_BINDING = 1;

    private final ShaderLoader shaderLoader;
    private final MeshRenderSystem meshRenderSystem;

    private RenderBackend backend;
    private Font font;
    private PipelineHandle occludedPipeline;
    private PipelineHandle overlayPipeline;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private MeshHandle mesh;
    private BindingSetHandle bindings;
    private int quadCount;
    private int occludedQuadCount;

    private final ByteBuffer vertexScratch =
            BufferUtils.createByteBuffer(MAX_QUADS * VERTICES_PER_QUAD * VERTEX_BYTES);
    private final ByteBuffer indexScratch =
            BufferUtils.createByteBuffer(MAX_QUADS * INDICES_PER_QUAD * Integer.BYTES);
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Vector3f cameraPosition = new Vector3f();
    private final Vector3f rightAxis = new Vector3f();
    private final Vector3f upAxis = new Vector3f();
    private final Vector3f anchor = new Vector3f();
    private final Vector3f corner = new Vector3f();

    public WorldTextRenderSystem(ShaderLoader shaderLoader, MeshRenderSystem meshRenderSystem) {
        this.shaderLoader = shaderLoader;
        this.meshRenderSystem = meshRenderSystem;
    }

    @Override
    public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
        this.backend = renderBackend;
        font = Font.loadFromResource(renderBackend, FONT_RESOURCE, FONT_PIXEL_HEIGHT, SamplerFilter.LINEAR);
        BindingSetLayout layout = new BindingSetLayout(List.of(
                new BindingSlot(FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(ATLAS_BINDING, BindingType.SAMPLED_TEXTURE_2D)));
        occludedPipeline = createPipeline(layout, DepthTest.LESS_EQUAL);
        overlayPipeline = createPipeline(layout, DepthTest.DISABLED);
        allocateMeshBuffers();
        bindings = renderBackend.createBindingSet(new BindingSetDescriptor(layout, List.of(
                new Binding(FRAME_UBO_BINDING, UniformBufferBinding.whole(
                        meshRenderSystem.frameUniformBuffer(), MeshShaderBindings.FRAME_UBO_SIZE)),
                new Binding(ATLAS_BINDING, new SampledTextureBinding(font.atlasTexture())))));
    }

    private PipelineHandle createPipeline(BindingSetLayout layout, DepthTest depthTest) {
        VertexLayout vertexLayout = new VertexLayout(List.of(
                new VertexAttribute(0, VertexFormat.FLOAT3, 0),
                new VertexAttribute(1, VertexFormat.FLOAT2, 12),
                new VertexAttribute(2, VertexFormat.FLOAT4, 20),
                new VertexAttribute(3, VertexFormat.FLOAT, 36)), VERTEX_BYTES);
        RenderState state = new RenderState(Topology.TRIANGLES, depthTest, BlendMode.ALPHA_BLEND, CullMode.NONE);
        ShaderSource source = new ShaderSource(shaderLoader.load(VERTEX_PATH).source(),
                shaderLoader.load(FRAGMENT_PATH).source());
        return backend.createPipeline(new PipelineDescriptor(source, vertexLayout, state, layout));
    }

    private void allocateMeshBuffers() {
        ByteBuffer vertexInit = BufferUtils.createByteBuffer(MAX_QUADS * VERTICES_PER_QUAD * VERTEX_BYTES);
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertexInit));
        ByteBuffer indexInit = BufferUtils.createByteBuffer(MAX_QUADS * INDICES_PER_QUAD * Integer.BYTES);
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, indexInit));
        mesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0,
                MAX_QUADS * INDICES_PER_QUAD, IndexFormat.UINT32));
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
        List<WorldText> texts = scene.componentsOf(WorldText.class);
        if (texts.isEmpty() || context.primaryCamera().isEmpty()) {
            return;
        }
        readCameraAxes(context);
        vertexScratch.clear();
        indexScratch.clear();
        quadCount = 0;
        appendPass(texts, true);
        occludedQuadCount = quadCount;
        appendPass(texts, false);
        if (quadCount <= 0) {
            return;
        }
        uploadAndSubmit(frame);
    }

    private void appendPass(List<WorldText> texts, boolean occluded) {
        for (WorldText text : texts) {
            if (text.activeInHierarchy() && text.drawable() && text.occluded() == occluded) {
                appendText(text);
            }
        }
    }

    private void uploadAndSubmit(FrameBuilder frame) {
        vertexScratch.flip();
        indexScratch.flip();
        backend.writeBuffer(vertexBuffer, vertexScratch, 0L);
        backend.writeBuffer(indexBuffer, indexScratch, 0L);
        if (occludedQuadCount > 0) {
            frame.submit(RenderPasses.TRANSPARENT_3D, DrawCommand.range(occludedPipeline, mesh, bindings,
                    0L, 0, occludedQuadCount * INDICES_PER_QUAD, ScissorRect.disabled()));
        }
        int overlayQuads = quadCount - occludedQuadCount;
        if (overlayQuads > 0) {
            frame.submit(RenderPasses.TRANSPARENT_3D, DrawCommand.range(overlayPipeline, mesh, bindings,
                    1L, occludedQuadCount * INDICES_PER_QUAD, overlayQuads * INDICES_PER_QUAD,
                    ScissorRect.disabled()));
        }
    }

    private void readCameraAxes(RenderContext context) {
        Camera3D camera = context.primaryCamera().get();
        camera.position(cameraPosition, context.interpolationAlpha());
        viewMatrix.set(camera.view(context.interpolationAlpha()));
        rightAxis.set(viewMatrix.m00(), viewMatrix.m10(), viewMatrix.m20()).normalize();
        upAxis.set(viewMatrix.m01(), viewMatrix.m11(), viewMatrix.m21()).normalize();
    }

    private void appendText(WorldText text) {
        text.anchor(anchor);
        float distance = anchor.distance(cameraPosition);
        float fade = text.fadeFactorAt(distance) * text.opacity();
        if (fade <= 0.0f) {
            return;
        }
        float metresPerPixel = scaleFor(text, distance) / font.pixelHeight();
        float startX = -font.measureWidth(text.text()) * 0.5f;
        emitGlyphs(text, startX, metresPerPixel, fade);
    }

    private float scaleFor(WorldText text, float distance) {
        if (!text.constantScreenSize()) {
            return text.lineHeight();
        }
        return text.lineHeight() * Math.max(distance, 0.001f);
    }

    private void emitGlyphs(WorldText text, float startX, float metresPerPixel, float alpha) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer penX = stack.floats(startX);
            FloatBuffer penY = stack.floats(0.0f);
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            String value = text.text();
            for (int index = 0; index < value.length() && quadCount < MAX_QUADS; index++) {
                if (font.appendQuad(value.charAt(index), penX, penY, quad)) {
                    appendQuad(quad, text, metresPerPixel, alpha);
                    quadCount++;
                }
            }
        }
    }

    private void appendQuad(STBTTAlignedQuad quad, WorldText text, float metresPerPixel, float alpha) {
        int base = quadCount * VERTICES_PER_QUAD;
        putVertex(quad.x0(), -quad.y1(), quad.s0(), quad.t1(), text, metresPerPixel, alpha);
        putVertex(quad.x1(), -quad.y1(), quad.s1(), quad.t1(), text, metresPerPixel, alpha);
        putVertex(quad.x1(), -quad.y0(), quad.s1(), quad.t0(), text, metresPerPixel, alpha);
        putVertex(quad.x0(), -quad.y0(), quad.s0(), quad.t0(), text, metresPerPixel, alpha);
        indexScratch.putInt(base).putInt(base + 1).putInt(base + 2);
        indexScratch.putInt(base).putInt(base + 2).putInt(base + 3);
    }

    private void putVertex(float localX, float localY, float u, float v,
                           WorldText text, float metresPerPixel, float alpha) {
        corner.set(anchor)
                .fma(localX * metresPerPixel, rightAxis)
                .fma(localY * metresPerPixel, upAxis);
        vertexScratch.putFloat(corner.x).putFloat(corner.y).putFloat(corner.z);
        vertexScratch.putFloat(u).putFloat(v);
        vertexScratch.putFloat(text.colour().x).putFloat(text.colour().y)
                .putFloat(text.colour().z).putFloat(alpha);
        vertexScratch.putFloat(text.outlineStrength());
    }

    @Override
    public void shutdown(RenderBackend renderBackend) {
        renderBackend.destroy(bindings);
        renderBackend.destroy(mesh);
        renderBackend.destroy(vertexBuffer);
        renderBackend.destroy(indexBuffer);
        renderBackend.destroy(occludedPipeline);
        renderBackend.destroy(overlayPipeline);
        font.destroy(renderBackend);
    }
}
