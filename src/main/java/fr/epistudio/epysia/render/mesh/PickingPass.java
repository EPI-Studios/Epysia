package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
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
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.ShaderSource;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.backend.VertexAttribute;
import fr.epistudio.epysia.render.backend.VertexFormat;
import fr.epistudio.epysia.render.backend.VertexLayout;
import fr.epistudio.epysia.render.shader.LoadedShader;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PickingPass {

    private static final String VERTEX_PATH = "picking.vert.glsl";
    private static final String FRAGMENT_PATH = "picking.frag.glsl";

    private final ShaderLoader shaderLoader;
    private final Logger logger;
    private final ByteBuffer scratchFrameUbo = BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE);
    private final ByteBuffer scratchObjectUbo = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
    private final ByteBuffer scratchPickingUbo = BufferUtils.createByteBuffer(MeshShaderBindings.PICKING_UBO_SIZE);
    private final Map<MeshRenderer, PerRenderer> resourcesByRenderer = new IdentityHashMap<>();
    private final Set<MeshRenderer> loggedSkinnedExclusions =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private RenderBackend backend;
    private BindingSetLayout bindingLayout;
    private PipelineHandle pipeline;
    private BufferHandle frameUbo;
    private BufferHandle pickingUbo;
    private TextureHandle colorTexture;
    private TextureHandle depthTexture;
    private RenderTargetHandle target;
    private int currentWidth;
    private int currentHeight;
    private boolean initialized;

    public PickingPass(ShaderLoader shaderLoader, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.logger = logger;
    }

    private void lazyInitialize(RenderBackend backend) {
        if (initialized) {
            return;
        }
        this.backend = backend;
        bindingLayout = new BindingSetLayout(List.of(
                new BindingSlot(MeshShaderBindings.FRAME_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.OBJECT_UBO_BINDING, BindingType.UNIFORM_BUFFER),
                new BindingSlot(MeshShaderBindings.PICKING_UBO_BINDING, BindingType.UNIFORM_BUFFER)
        ));
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT3, 0);
        VertexLayout layout = new VertexLayout(List.of(position), MeshShaderBindings.VERTEX_STRIDE);
        pipeline = backend.createPipeline(new PipelineDescriptor(
                loadShaderSource(), layout, RenderState.OPAQUE_3D, bindingLayout));
        frameUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(MeshShaderBindings.FRAME_UBO_SIZE)));
        pickingUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(MeshShaderBindings.PICKING_UBO_SIZE)));
        initialized = true;
    }

    public Optional<GameObject> pickAt(Scene scene, Camera3D camera, int x, int y, int width, int height,
                                       RenderBackend renderBackend) {
        lazyInitialize(renderBackend);
        ensureTargetSize(width, height);
        writeFrameUbo(camera);

        backend.beginPass(target, PassClear.color(0.0f, 0.0f, 0.0f));
        List<GameObject> gameObjects = scene.gameObjects();
        for (int index = 0; index < gameObjects.size(); index++) {
            GameObject gameObject = gameObjects.get(index);
            Optional<MeshRenderer> rendererOpt = gameObject.getComponent(MeshRenderer.class);
            Optional<Transform3D> transformOpt = gameObject.getComponent(Transform3D.class);
            if (rendererOpt.isEmpty() || transformOpt.isEmpty()) {
                continue;
            }
            MeshRenderer renderer = rendererOpt.get();
            Optional<UploadedMesh> meshOpt = renderer.mesh();
            if (meshOpt.isEmpty()) {
                continue;
            }
            UploadedMesh mesh = meshOpt.get();
            if (mesh.skinned()) {
                logExclusionOnce(gameObject, renderer, "Skinned");
                continue;
            }
            if (mesh.vertexColored()) {
                logExclusionOnce(gameObject, renderer, "Vertex-colored");
                continue;
            }
            if (!allSubmeshesAlive(mesh)) {
                continue;
            }
            PerRenderer perRenderer = resourcesByRenderer.computeIfAbsent(renderer, this::createPerRenderer);
            writeObjectUbo(perRenderer.modelUbo(), transformOpt.get().worldMatrix());
            writePickingId(index + 1);
            for (UploadedSubmesh submesh : mesh.submeshes()) {
                backend.execute(new DrawCommand(pipeline, submesh.handle(), perRenderer.bindings(), 0L, 1));
            }
        }
        backend.endPass();

        int pixelArgb = backend.readPixelArgb(target, x, currentHeight - y - 1);
        int decoded = decodeId(pixelArgb);
        if (decoded <= 0 || decoded > gameObjects.size()) {
            return Optional.empty();
        }
        return Optional.of(gameObjects.get(decoded - 1));
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        for (PerRenderer perRenderer : resourcesByRenderer.values()) {
            backend.destroy(perRenderer.bindings());
            backend.destroy(perRenderer.modelUbo());
        }
        resourcesByRenderer.clear();
        loggedSkinnedExclusions.clear();
        backend.destroy(pipeline);
        backend.destroy(frameUbo);
        backend.destroy(pickingUbo);
        if (target != null) {
            backend.destroy(target);
        }
        if (colorTexture != null) {
            backend.destroy(colorTexture);
        }
        if (depthTexture != null) {
            backend.destroy(depthTexture);
        }
        initialized = false;
    }

    private boolean allSubmeshesAlive(UploadedMesh mesh) {
        for (UploadedSubmesh submesh : mesh.submeshes()) {
            if (!backend.isAlive(submesh.handle())) {
                return false;
            }
        }
        return !mesh.submeshes().isEmpty();
    }

    private void logExclusionOnce(GameObject gameObject, MeshRenderer renderer, String reason) {
        if (!loggedSkinnedExclusions.add(renderer)) {
            return;
        }
        logger.info(reason + " mesh '" + gameObject.name() + "' excluded from picking this milestone.");
    }

    private PerRenderer createPerRenderer(MeshRenderer ignored) {
        ByteBuffer empty = BufferUtils.createByteBuffer(MeshShaderBindings.OBJECT_UBO_SIZE);
        BufferHandle modelUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, empty));
        BindingSetHandle bindings = backend.createBindingSet(new BindingSetDescriptor(bindingLayout, List.of(
                new Binding(MeshShaderBindings.FRAME_UBO_BINDING,
                        UniformBufferBinding.whole(frameUbo, MeshShaderBindings.FRAME_UBO_SIZE)),
                new Binding(MeshShaderBindings.OBJECT_UBO_BINDING,
                        UniformBufferBinding.whole(modelUbo, MeshShaderBindings.OBJECT_UBO_SIZE)),
                new Binding(MeshShaderBindings.PICKING_UBO_BINDING,
                        UniformBufferBinding.whole(pickingUbo, MeshShaderBindings.PICKING_UBO_SIZE))
        )));
        return new PerRenderer(modelUbo, bindings);
    }

    private void ensureTargetSize(int width, int height) {
        if (target != null && width == currentWidth && height == currentHeight) {
            return;
        }
        if (target != null) {
            backend.destroy(target);
            backend.destroy(colorTexture);
            backend.destroy(depthTexture);
        }
        currentWidth = width;
        currentHeight = height;
        colorTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.NEAREST));
        depthTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT, SamplerFilter.NEAREST));
        target = backend.createRenderTarget(new RenderTargetDescriptor(width, height,
                List.of(colorTexture), Optional.of(depthTexture)));
    }

    private void writeFrameUbo(Camera3D camera) {
        Matrix4f viewProjection = camera.viewProjection();
        scratchFrameUbo.clear();
        viewProjection.get(0, scratchFrameUbo);
        for (int i = 16; i < MeshShaderBindings.FRAME_UBO_SIZE / 4; i++) {
            scratchFrameUbo.putFloat(i * 4, 0.0f);
        }
        scratchFrameUbo.limit(MeshShaderBindings.FRAME_UBO_SIZE);
        scratchFrameUbo.position(0);
        backend.writeBuffer(frameUbo, scratchFrameUbo, 0L);
    }

    private void writeObjectUbo(BufferHandle ubo, Matrix4f modelMatrix) {
        scratchObjectUbo.clear();
        modelMatrix.get(0, scratchObjectUbo);
        scratchObjectUbo.position(0);
        scratchObjectUbo.limit(MeshShaderBindings.OBJECT_UBO_SIZE);
        backend.writeBuffer(ubo, scratchObjectUbo, 0L);
    }

    private void writePickingId(int id) {
        float r = (id & 0xFF) / 255.0f;
        float g = ((id >> 8) & 0xFF) / 255.0f;
        float b = ((id >> 16) & 0xFF) / 255.0f;
        scratchPickingUbo.clear();
        scratchPickingUbo.putFloat(r).putFloat(g).putFloat(b).putFloat(1.0f);
        scratchPickingUbo.position(0);
        scratchPickingUbo.limit(MeshShaderBindings.PICKING_UBO_SIZE);
        backend.writeBuffer(pickingUbo, scratchPickingUbo, 0L);
    }

    private static int decodeId(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return r | (g << 8) | (b << 16);
    }

    private ShaderSource loadShaderSource() {
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        return new ShaderSource(vertex.source(), fragment.source());
    }

    private record PerRenderer(BufferHandle modelUbo, BindingSetHandle bindings) {
    }
}
