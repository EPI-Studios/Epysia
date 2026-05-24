package fr.epistudio.epysia.editor;

import com.miry.graphics.Texture;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.PointLight;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.EpysiaFrameDriver;
import fr.epistudio.epysia.render.Stage;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.mesh.CapsuleMesh;
import fr.epistudio.epysia.render.mesh.CubeMesh;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.PlaneMesh;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.texture.Texture2D;
import fr.epistudio.epysia.scene.Scene;

import java.util.List;
import java.util.Optional;

public final class EditorSceneHost implements AutoCloseable {

    private static final PassClear SCENE_CLEAR = PassClear.color(0.110f, 0.124f, 0.149f);
    private static final PassClear NO_CLEAR = PassClear.none();

    private final OpenGlRenderBackend backend;
    private final EditorRenderSurface renderSurface;
    private final Scene scene;
    private final EpysiaFrameDriver driver;
    private final ShaderLoader shaderLoader;
    private final ShaderWatcher shaderWatcher;
    private Logger logger;
    private final EditorPrimitiveRegistry primitiveRegistry = new EditorPrimitiveRegistry();
    private final EditorComponentRegistry componentRegistry = new EditorComponentRegistry();

    private TextureHandle colorTexture;
    private TextureHandle depthTexture;
    private RenderTargetHandle renderTarget;
    private int currentWidth;
    private int currentHeight;
    private boolean initialized;
    private Texture cachedColorWrapper;
    private int cachedColorGlName;

    private UploadedMesh cubeMesh;
    private UploadedMesh planeMesh;
    private UploadedMesh capsuleMesh;
    private LitMaterial whiteMaterial;
    private LitMaterial groundMaterial;
    private TextureHandle defaultAlbedo;
    private TextureHandle groundAlbedo;
    private GameObject editorCameraObject;

    public EditorSceneHost() {
        this.backend = new OpenGlRenderBackend();
        this.renderSurface = new EditorRenderSurface();
        this.scene = new Scene("editor");
        this.driver = new EpysiaFrameDriver(backend, SCENE_CLEAR);
        this.shaderLoader = ShaderLoader.autoDetect();
        this.shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
        this.logger = new ConsoleLogger();
    }

    public void setLogger(Logger logger) {
        if (logger != null) {
            this.logger = logger;
        }
    }

    public void initialize(int initialWidth, int initialHeight) {
        renderSurface.setSize(initialWidth, initialHeight);
        backend.initialize(renderSurface);
        currentWidth = renderSurface.framebufferWidth();
        currentHeight = renderSurface.framebufferHeight();
        createRenderTarget(currentWidth, currentHeight);
        driver.addRenderSystem(new MeshRenderSystem(shaderLoader, shaderWatcher, logger, renderSurface));
        driver.initializeRenderSystems();
        bindStagesToEditorTarget();
        loadSharedResources();
        populateDefaultScene();
        registerPrimitives();
        initialized = true;
    }

    public EditorComponentRegistry components() {
        return componentRegistry;
    }

    public EditorPrimitiveRegistry primitiveRegistry() {
        return primitiveRegistry;
    }

    private void loadSharedResources() {
        cubeMesh = MeshUploader.upload(backend, CubeMesh.data());
        planeMesh = MeshUploader.upload(backend, PlaneMesh.data(20.0f, 20.0f));
        capsuleMesh = MeshUploader.upload(backend, CapsuleMesh.data());
        defaultAlbedo = Texture2D.checkerboard(backend, 256, 32);
        groundAlbedo = Texture2D.checkerboard(backend, 512, 64);
        whiteMaterial = new LitMaterial()
                .setAlbedo(defaultAlbedo)
                .setBaseColor(0.85f, 0.85f, 0.95f);
        groundMaterial = new LitMaterial()
                .setAlbedo(groundAlbedo)
                .setBaseColor(0.45f, 0.55f, 0.50f);
    }

    private void populateDefaultScene() {
        editorCameraObject = new GameObject("Editor Camera");
        Transform3D editorCameraTransform = new Transform3D().setPosition(6.0f, 5.0f, 8.0f);
        editorCameraTransform.lookAt(0.0f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f);
        editorCameraObject.addComponent(editorCameraTransform);
        editorCameraObject.addComponent(new Camera3D()
                .setFieldOfViewDegrees(60.0f)
                .setNearFar(0.05f, 500.0f));

        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D().lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f));
        sun.addComponent(new DirectionalLight()
                .setColor(1.00f, 0.95f, 0.85f)
                .setAmbient(0.22f, 0.24f, 0.28f)
                .setShadowExtent(8.0f, 0.5f, 30.0f));

        GameObject ground = new GameObject("Ground");
        ground.addComponent(new Transform3D().setPosition(0.0f, -0.05f, 0.0f));
        ground.addComponent(new MeshRenderer().setMesh(planeMesh).setMaterial(groundMaterial));

        scene.addGameObject(editorCameraObject);
        scene.addGameObject(sun);
        scene.addGameObject(ground);
        scene.advanceTick();
    }

    private void registerPrimitives() {
        primitiveRegistry.register("Empty", () -> {
            GameObject object = new GameObject("Empty");
            object.addComponent(new Transform3D());
            return object;
        });
        primitiveRegistry.register("Cube", () -> {
            GameObject object = new GameObject("Cube");
            object.addComponent(new Transform3D().setPosition(0.0f, 0.5f, 0.0f));
            object.addComponent(new MeshRenderer().setMesh(cubeMesh).setMaterial(whiteMaterial));
            return object;
        });
        primitiveRegistry.register("Plane (20m)", () -> {
            GameObject object = new GameObject("Plane");
            object.addComponent(new Transform3D());
            object.addComponent(new MeshRenderer().setMesh(planeMesh).setMaterial(groundMaterial));
            return object;
        });
        primitiveRegistry.register("Directional Light", () -> {
            GameObject object = new GameObject("Directional Light");
            object.addComponent(new Transform3D().lookAt(-0.3f, -1.0f, -0.2f, 0.0f, 1.0f, 0.0f));
            object.addComponent(new DirectionalLight()
                    .setColor(1.0f, 0.95f, 0.85f)
                    .setAmbient(0.20f, 0.22f, 0.26f)
                    .setShadowExtent(8.0f, 0.5f, 30.0f));
            return object;
        });
        primitiveRegistry.register("Point Light", () -> {
            GameObject object = new GameObject("Point Light");
            object.addComponent(new Transform3D().setPosition(0.0f, 2.0f, 0.0f));
            object.addComponent(new PointLight()
                    .setColor(1.0f, 0.85f, 0.55f)
                    .setIntensity(6.0f)
                    .setRange(8.0f));
            return object;
        });
        primitiveRegistry.register("Camera", () -> {
            GameObject object = new GameObject("Camera");
            Transform3D transform = new Transform3D().setPosition(0.0f, 1.6f, 5.0f);
            transform.lookAt(0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f);
            object.addComponent(transform);
            object.addComponent(new Camera3D().setFieldOfViewDegrees(60.0f).setNearFar(0.05f, 500.0f));
            return object;
        });
        primitiveRegistry.register("Capsule", () -> {
            GameObject object = new GameObject("Capsule");
            object.addComponent(new Transform3D().setPosition(0.0f, 1.3f, 0.0f));
            object.addComponent(new MeshRenderer().setMesh(capsuleMesh).setMaterial(whiteMaterial));
            return object;
        });
    }

    public void ensureViewportSize(int requestedWidth, int requestedHeight) {
        int desiredWidth = Math.max(1, requestedWidth);
        int desiredHeight = Math.max(1, requestedHeight);
        if (desiredWidth == currentWidth && desiredHeight == currentHeight) {
            return;
        }
        currentWidth = desiredWidth;
        currentHeight = desiredHeight;
        renderSurface.setSize(currentWidth, currentHeight);
        destroyRenderTarget();
        createRenderTarget(currentWidth, currentHeight);
        bindStagesToEditorTarget();
        driver.onResize(currentWidth, currentHeight);
        bindStagesToEditorTarget();
    }

    private void createRenderTarget(int width, int height) {
        colorTexture = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.RGBA8,
                TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        depthTexture = backend.createTexture(new TextureDescriptor(width, height, TextureFormat.DEPTH32F,
                TextureUsage.SAMPLED_DEPTH_ATTACHMENT, SamplerFilter.LINEAR));
        renderTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                width, height, List.of(colorTexture), Optional.of(depthTexture)));
    }

    private void destroyRenderTarget() {
        if (renderTarget != null) {
            backend.destroy(renderTarget);
            renderTarget = null;
        }
        if (colorTexture != null) {
            backend.destroy(colorTexture);
            colorTexture = null;
        }
        if (depthTexture != null) {
            backend.destroy(depthTexture);
            depthTexture = null;
        }
        cachedColorWrapper = null;
        cachedColorGlName = 0;
    }

    private void bindStagesToEditorTarget() {
        driver.bindStageTarget(Stage.OPAQUE_3D, renderTarget, SCENE_CLEAR);
        driver.bindStageTarget(Stage.TRANSPARENT_3D, renderTarget, NO_CLEAR);
        driver.bindStageTarget(Stage.WORLD_2D, renderTarget, NO_CLEAR);
    }

    public void renderFrame() {
        if (!initialized) {
            return;
        }
        driver.renderFrame(scene, 0.0f);
    }

    public Texture colorTextureForMiry() {
        if (colorTexture == null) {
            return null;
        }
        int currentGlName = backend.glTextureName(colorTexture);
        if (cachedColorWrapper == null || cachedColorGlName != currentGlName) {
            cachedColorWrapper = Texture.wrapExternal(currentGlName, currentWidth, currentHeight, false);
            cachedColorGlName = currentGlName;
        }
        return cachedColorWrapper;
    }

    public int currentWidth() {
        return currentWidth;
    }

    public int currentHeight() {
        return currentHeight;
    }

    public Scene scene() {
        return scene;
    }

    public OpenGlRenderBackend backend() {
        return backend;
    }

    public Camera3D camera() {
        return editorCameraObject.getComponent(Camera3D.class).orElseThrow();
    }

    public Transform3D cameraTransform() {
        return editorCameraObject.getComponent(Transform3D.class).orElseThrow();
    }

    public GameObject editorCameraObject() {
        return editorCameraObject;
    }

    public EditorPrimitiveRegistry primitives() {
        return primitiveRegistry;
    }

    @Override
    public void close() {
        if (!initialized) {
            return;
        }
        destroyRenderTarget();
        driver.shutdownRenderSystems();
        backend.shutdown();
        initialized = false;
    }
}
