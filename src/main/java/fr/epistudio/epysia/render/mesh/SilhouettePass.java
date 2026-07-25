package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
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
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.SamplerFilter;
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
import fr.epistudio.epysia.render.environment.FullscreenQuad;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SilhouettePass {

    public static final float MAXIMUM_OUTLINE_RADIUS = 6.0f;

    private static final String MASK_VERTEX_PATH = "picking.vert.glsl";
    private static final String MASK_FRAGMENT_PATH = "picking.frag.glsl";
    private static final String OUTLINE_VERTEX_PATH = "post.vert.glsl";
    private static final String OUTLINE_FRAGMENT_PATH = "silhouette_outline.frag.glsl";
    private static final int OUTLINE_UBO_SIZE = 32;
    private static final int OUTLINE_UBO_BINDING = 0;
    private static final int MASK_TEXTURE_BINDING = 1;
    private static final RenderState MASK_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.OPAQUE, CullMode.NONE, false);
    private static final RenderState OUTLINE_STATE = new RenderState(
            Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.ALPHA_BLEND, CullMode.NONE, false);

    private final ShaderLoader shaderLoader;
    private final FullscreenQuad quad = new FullscreenQuad();
    private final ByteBuffer scratchFrameUbo = BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE);
    private final ByteBuffer scratchObjectUbo = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
    private final ByteBuffer scratchMaskUbo = BufferUtils.createByteBuffer(MeshShaderBindings.PICKING_UBO_SIZE);
    private final ByteBuffer scratchOutlineUbo = BufferUtils.createByteBuffer(OUTLINE_UBO_SIZE);
    private final Map<MeshRenderer, PerRenderer> resourcesByRenderer = new IdentityHashMap<>();

    private RenderBackend backend;
    private BindingSetLayout maskLayout;
    private BindingSetLayout outlineLayout;
    private PipelineHandle maskPipeline;
    private PipelineHandle outlinePipeline;
    private BufferHandle frameUbo;
    private BufferHandle maskUbo;
    private BufferHandle outlineUbo;
    private BindingSetHandle outlineBindings;
    private TextureHandle maskTexture;
    private TextureHandle outlineTexture;
    private RenderTargetHandle maskTarget;
    private RenderTargetHandle outlineTarget;
    private int currentWidth;
    private int currentHeight;
    private boolean initialized;

    public SilhouettePass(ShaderLoader shaderLoader) {
        this.shaderLoader = shaderLoader;
    }

    public Optional<TextureHandle> render(List<GameObject> gameObjects, Matrix4f viewProjection,
                                          int width, int height, float outlineRadiusPixels,
                                          Vector3f color, float fillAlpha, RenderBackend renderBackend) {
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        lazyInitialize(renderBackend);
        ensureTargetSize(width, height);
        writeFrameUbo(viewProjection);
        drawMask(gameObjects);
        drawOutline(outlineRadiusPixels, color, fillAlpha);
        return Optional.of(outlineTexture);
    }

    private void drawMask(List<GameObject> gameObjects) {
        backend.beginPass(maskTarget, PassClear.color(0.0f, 0.0f, 0.0f));
        writeMaskColor();
        for (GameObject gameObject : gameObjects) {
            drawGameObjectMask(gameObject);
        }
        backend.endPass();
    }

    private void drawGameObjectMask(GameObject gameObject) {
        Optional<MeshRenderer> renderer = gameObject.getComponent(MeshRenderer.class);
        Optional<Transform3D> transform = gameObject.getComponent(Transform3D.class);
        if (renderer.isEmpty() || transform.isEmpty()) {
            return;
        }
        Optional<UploadedMesh> mesh = renderer.get().mesh();
        if (mesh.isEmpty() || mesh.get().skinned() || mesh.get().vertexColored()) {
            return;
        }
        PerRenderer resources = resourcesByRenderer.computeIfAbsent(renderer.get(), this::createPerRenderer);
        writeObjectUbo(resources.modelUbo(), transform.get().worldMatrix());
        for (UploadedSubmesh submesh : mesh.get().submeshes()) {
            backend.execute(new DrawCommand(maskPipeline, submesh.handle(), resources.bindings(), 0L, 1));
        }
    }

    private void drawOutline(float outlineRadiusPixels, Vector3f color, float fillAlpha) {
        writeOutlineUbo(outlineRadiusPixels, color, fillAlpha);
        backend.beginPass(outlineTarget, PassClear.color(0.0f, 0.0f, 0.0f));
        backend.execute(DrawCommand.of(outlinePipeline, quad.mesh(), outlineBindings));
        backend.endPass();
    }

    private void lazyInitialize(RenderBackend renderBackend) {
        if (initialized) {
            return;
        }
        this.backend = renderBackend;
        quad.initialize(renderBackend);
        createMaskPipeline();
        createOutlinePipeline();
        initialized = true;
    }

    private void createMaskPipeline() {
        maskLayout = new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.PICKING_UBO_BINDING, BindingType.UNIFORM_BUFFER)));
        VertexLayout layout = new VertexLayout(
                List.of(new VertexAttribute(0, VertexFormat.FLOAT3, 0)), MeshShaderBindings.VERTEX_STRIDE);
        maskPipeline = backend.createPipeline(new PipelineDescriptor(
                sourceOf(MASK_VERTEX_PATH, MASK_FRAGMENT_PATH), layout, MASK_STATE, maskLayout));
        frameUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE)));
        maskUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(MeshShaderBindings.PICKING_UBO_SIZE)));
    }

    private void createOutlinePipeline() {
        outlineLayout = new BindingSetLayout(List.of(
                new BindingSlot(OUTLINE_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MASK_TEXTURE_BINDING, BindingType.SAMPLED_TEXTURE_2D)));
        outlinePipeline = backend.createPipeline(new PipelineDescriptor(
                sourceOf(OUTLINE_VERTEX_PATH, OUTLINE_FRAGMENT_PATH), FullscreenQuad.LAYOUT,
                OUTLINE_STATE, outlineLayout));
        outlineUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(OUTLINE_UBO_SIZE)));
    }

    private ShaderSource sourceOf(String vertexPath, String fragmentPath) {
        LoadedShader vertex = shaderLoader.load(vertexPath);
        LoadedShader fragment = shaderLoader.load(fragmentPath);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    private PerRenderer createPerRenderer(MeshRenderer ignored) {
        BufferHandle modelUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE)));
        BindingSetHandle bindings = backend.createBindingSet(new BindingSetDescriptor(maskLayout, List.of(
                new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                        UniformBufferBinding.whole(frameUbo, MeshShaderBindings.FRAME_UBO_SIZE)),
                new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                        UniformBufferBinding.whole(modelUbo, MeshShaderBindings.OBJECT_UBO_SIZE)),
                new Binding(MeshShaderBindings.PICKING_UBO_BINDING,
                        UniformBufferBinding.whole(maskUbo, MeshShaderBindings.PICKING_UBO_SIZE)))));
        return new PerRenderer(modelUbo, bindings);
    }

    private void ensureTargetSize(int width, int height) {
        if (maskTarget != null && width == currentWidth && height == currentHeight) {
            return;
        }
        destroyTargets();
        currentWidth = width;
        currentHeight = height;
        maskTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.NEAREST));
        outlineTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        maskTarget = backend.createRenderTarget(
                new RenderTargetDescriptor(width, height, List.of(maskTexture), Optional.empty()));
        outlineTarget = backend.createRenderTarget(
                new RenderTargetDescriptor(width, height, List.of(outlineTexture), Optional.empty()));
        rebuildOutlineBindings();
    }

    private void rebuildOutlineBindings() {
        if (outlineBindings != null) {
            backend.destroy(outlineBindings);
        }
        outlineBindings = backend.createBindingSet(new BindingSetDescriptor(outlineLayout, List.of(
                new Binding(OUTLINE_UBO_BINDING, UniformBufferBinding.whole(outlineUbo, OUTLINE_UBO_SIZE)),
                new Binding(MASK_TEXTURE_BINDING, new SampledTextureBinding(maskTexture)))));
    }

    private void writeFrameUbo(Matrix4f viewProjection) {
        scratchFrameUbo.clear();
        viewProjection.get(0, scratchFrameUbo);
        for (int index = 16; index < MeshShaderBindings.FRAME_UBO_SIZE / 4; index++) {
            scratchFrameUbo.putFloat(index * 4, 0.0f);
        }
        scratchFrameUbo.position(0);
        scratchFrameUbo.limit(MeshShaderBindings.FRAME_UBO_SIZE);
        backend.writeBuffer(frameUbo, scratchFrameUbo, 0L);
    }

    private void writeObjectUbo(BufferHandle ubo, Matrix4f modelMatrix) {
        scratchObjectUbo.clear();
        modelMatrix.get(0, scratchObjectUbo);
        scratchObjectUbo.position(0);
        scratchObjectUbo.limit(MeshShaderBindings.OBJECT_UBO_SIZE);
        backend.writeBuffer(ubo, scratchObjectUbo, 0L);
    }

    private void writeMaskColor() {
        scratchMaskUbo.clear();
        scratchMaskUbo.putFloat(1.0f).putFloat(1.0f).putFloat(1.0f).putFloat(1.0f);
        scratchMaskUbo.position(0);
        scratchMaskUbo.limit(MeshShaderBindings.PICKING_UBO_SIZE);
        backend.writeBuffer(maskUbo, scratchMaskUbo, 0L);
    }

    private void writeOutlineUbo(float outlineRadiusPixels, Vector3f color, float fillAlpha) {
        float radius = Math.clamp(outlineRadiusPixels, 1.0f, MAXIMUM_OUTLINE_RADIUS);
        scratchOutlineUbo.clear();
        scratchOutlineUbo.putFloat(color.x).putFloat(color.y).putFloat(color.z).putFloat(1.0f);
        scratchOutlineUbo.putFloat(1.0f / currentWidth).putFloat(1.0f / currentHeight)
                .putFloat(radius).putFloat(fillAlpha);
        scratchOutlineUbo.position(0);
        scratchOutlineUbo.limit(OUTLINE_UBO_SIZE);
        backend.writeBuffer(outlineUbo, scratchOutlineUbo, 0L);
    }

    private void destroyTargets() {
        if (maskTarget == null) {
            return;
        }
        backend.destroy(maskTarget);
        backend.destroy(outlineTarget);
        backend.destroy(maskTexture);
        backend.destroy(outlineTexture);
        maskTarget = null;
        outlineTarget = null;
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        for (PerRenderer resources : resourcesByRenderer.values()) {
            backend.destroy(resources.bindings());
            backend.destroy(resources.modelUbo());
        }
        resourcesByRenderer.clear();
        if (outlineBindings != null) {
            backend.destroy(outlineBindings);
            outlineBindings = null;
        }
        destroyTargets();
        backend.destroy(maskPipeline);
        backend.destroy(outlinePipeline);
        backend.destroy(frameUbo);
        backend.destroy(maskUbo);
        backend.destroy(outlineUbo);
        quad.shutdown();
        initialized = false;
    }

    private record PerRenderer(BufferHandle modelUbo, BindingSetHandle bindings) {
    }
}
