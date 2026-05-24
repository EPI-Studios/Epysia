package fr.epistudio.epysia.render.postfx;

import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.FrameBuilder;
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
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineDescriptor;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderState;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
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
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.render.backend.RenderSurface;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

public final class PostProcessSystem implements RenderSystem {

    private static final String VERTEX_PATH = "post.vert.glsl";
    private static final String FRAGMENT_PATH = "post.frag.glsl";
    private static final int UBO_SIZE = 160;
    private static final float[] QUAD_VERTICES = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
             1.0f,  1.0f,
            -1.0f,  1.0f
    };
    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    private final ShaderLoader shaderLoader;
    private final RenderSurface window;
    private final Logger logger;
    private final PostProcessSettings settings = new PostProcessSettings();
    private final Matrix4f scratchInverseViewProjection = new Matrix4f();
    private final Vector3f scratchCameraPosition = new Vector3f();

    private RenderBackend backend;
    private TextureHandle sceneColorTexture;
    private TextureHandle sceneDepthTexture;
    private RenderTargetHandle sceneTarget;
    private PipelineHandle pipeline;
    private BufferHandle vertexBuffer;
    private BufferHandle indexBuffer;
    private BufferHandle postUbo;
    private MeshHandle quadMesh;
    private BindingSetHandle bindings;
    private final ByteBuffer uboScratch = BufferUtils.createByteBuffer(UBO_SIZE);

    public PostProcessSystem(ShaderLoader shaderLoader, RenderSurface window, Logger logger) {
        this.shaderLoader = shaderLoader;
        this.window = window;
        this.logger = logger;
    }

    public PostProcessSettings settings() {
        return settings;
    }

    @Override
    public void initialize(RenderBackend backend, StageConfigurer configurer) {
        this.backend = backend;
        createSceneTarget();
        pipeline = backend.createPipeline(buildPipelineDescriptor(buildLayout()));
        allocateQuadMesh();
        createPostUbo();
        bindings = createBindings();
        bindStageTargets(configurer);
    }

    private BindingSetLayout buildLayout() {
        return new BindingSetLayout(List.of(
                new BindingSlot(0, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(1, BindingType.UNIFORM_BUFFER),
                new BindingSlot(2, BindingType.SAMPLED_TEXTURE_2D)
        ));
    }

    private BindingSetHandle createBindings() {
        return backend.createBindingSet(new BindingSetDescriptor(buildLayout(),
                List.of(
                        new Binding(0, new SampledTextureBinding(sceneColorTexture)),
                        new Binding(1, UniformBufferBinding.whole(postUbo, UBO_SIZE)),
                        new Binding(2, new SampledTextureBinding(sceneDepthTexture))
                )));
    }

    private void createSceneTarget() {
        int width = Math.max(1, window.framebufferWidth());
        int height = Math.max(1, window.framebufferHeight());
        sceneColorTexture = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.RGBA8, TextureUsage.SAMPLED));
        sceneDepthTexture = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT));
        sceneTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                width, height, List.of(sceneColorTexture), Optional.of(sceneDepthTexture)
        ));
    }

    @Override
    public void onResize(RenderBackend backend, StageConfigurer configurer, int width, int height) {
        backend.destroy(bindings);
        backend.destroy(sceneTarget);
        backend.destroy(sceneColorTexture);
        backend.destroy(sceneDepthTexture);
        createSceneTarget();
        bindings = createBindings();
        bindStageTargets(configurer);
    }

    private PipelineDescriptor buildPipelineDescriptor(BindingSetLayout layout) {
        VertexAttribute position = new VertexAttribute(0, VertexFormat.FLOAT2, 0);
        VertexLayout vertexLayout = new VertexLayout(List.of(position), 8);
        RenderState state = new RenderState(Topology.TRIANGLES, DepthTest.DISABLED, BlendMode.OPAQUE, CullMode.NONE);
        LoadedShader vertex = shaderLoader.load(VERTEX_PATH);
        LoadedShader fragment = shaderLoader.load(FRAGMENT_PATH);
        return new PipelineDescriptor(new ShaderSource(vertex.source(), fragment.source()), vertexLayout, state, layout);
    }

    private void allocateQuadMesh() {
        ByteBuffer vertexBytes = BufferUtils.createByteBuffer(QUAD_VERTICES.length * Float.BYTES);
        vertexBytes.asFloatBuffer().put(QUAD_VERTICES);
        vertexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.VERTEX, vertexBytes));
        ByteBuffer indexBytes = BufferUtils.createByteBuffer(QUAD_INDICES.length * Integer.BYTES);
        indexBytes.asIntBuffer().put(QUAD_INDICES);
        indexBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDEX, indexBytes));
        quadMesh = backend.createMesh(new MeshDescriptor(vertexBuffer, indexBuffer, 0, QUAD_INDICES.length, IndexFormat.UINT32));
    }

    private void createPostUbo() {
        ByteBuffer initial = BufferUtils.createByteBuffer(UBO_SIZE);
        postUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, initial));
    }

    private void bindStageTargets(StageConfigurer configurer) {
        PassClear sceneClear = PassClear.color(0.10f, 0.12f, 0.18f);
        PassClear sceneNoClear = PassClear.none();
        configurer.bindStageTarget(Stage.OPAQUE_3D, sceneTarget, sceneClear);
        configurer.bindStageTarget(Stage.TRANSPARENT_3D, sceneTarget, sceneNoClear);
        configurer.bindStageTarget(Stage.WORLD_2D, sceneTarget, sceneNoClear);
        configurer.bindStageTarget(Stage.POST, RenderTargetHandle.SCREEN, PassClear.color(0.0f, 0.0f, 0.0f));
    }

    @Override
    public void collect(Scene scene, FrameBuilder frame, float interpolationAlpha) {
        Camera3D camera = findCamera(scene);
        writeUbo(camera);
        frame.submit(Stage.POST, DrawCommand.of(pipeline, quadMesh, bindings));
    }

    private Camera3D findCamera(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            Optional<Camera3D> found = gameObject.getComponent(Camera3D.class);
            if (found.isPresent()) {
                return found.get();
            }
        }
        return null;
    }

    private void writeUbo(Camera3D camera) {
        uboScratch.clear();
        uboScratch.putFloat(settings.vignetteStrength()).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);
        uboScratch.putFloat(settings.gradeGamma()).putFloat(settings.gradeExposure()).putFloat(0.0f).putFloat(0.0f);
        writeFogParameters(camera);
        writeInverseViewProjection(camera);
        uboScratch.flip();
        backend.writeBuffer(postUbo, uboScratch, 0L);
    }

    private void writeFogParameters(Camera3D camera) {
        float fogStrength = (settings.fogEnabled() && camera != null) ? 1.0f : 0.0f;
        Vector3f fogColor = settings.fogColor();
        uboScratch.putFloat(fogColor.x).putFloat(fogColor.y).putFloat(fogColor.z).putFloat(fogStrength);
        uboScratch.putFloat(settings.fogDistanceStart()).putFloat(settings.fogDistanceDensity())
                .putFloat(settings.fogHeightOrigin()).putFloat(settings.fogHeightFalloff());
        float nearPlane = camera != null ? camera.nearPlane() : 0.1f;
        float farPlane = camera != null ? camera.farPlane() : 100.0f;
        Vector3f cameraPosition = camera != null ? camera.position(scratchCameraPosition) : scratchCameraPosition.set(0.0f);
        uboScratch.putFloat(nearPlane).putFloat(farPlane).putFloat(settings.fogHeightDensity()).putFloat(0.0f);
        uboScratch.putFloat(cameraPosition.x).putFloat(cameraPosition.y).putFloat(cameraPosition.z).putFloat(0.0f);
    }

    private void writeInverseViewProjection(Camera3D camera) {
        if (camera != null) {
            camera.viewProjection().invert(scratchInverseViewProjection);
        } else {
            scratchInverseViewProjection.identity();
        }
        scratchInverseViewProjection.get(uboScratch.position(), uboScratch);
        uboScratch.position(uboScratch.position() + 64);
    }

    @Override
    public void shutdown(RenderBackend backend) {
        backend.destroy(bindings);
        backend.destroy(postUbo);
        backend.destroy(quadMesh);
        backend.destroy(vertexBuffer);
        backend.destroy(indexBuffer);
        backend.destroy(pipeline);
        backend.destroy(sceneTarget);
        backend.destroy(sceneColorTexture);
        backend.destroy(sceneDepthTexture);
    }
}
