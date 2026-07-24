package fr.epistudio.epysia.editor.runtime;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.SystemRegistryImpl;
import fr.epistudio.epysia.assets.loaders.ClipAssetLoader;
import fr.epistudio.epysia.assets.loaders.MaterialAssetLoader;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.assets.loaders.PhysicsMaterialLoader;
import fr.epistudio.epysia.assets.loaders.ProbesAssetLoader;
import fr.epistudio.epysia.assets.loaders.SpriteAtlasAssetLoader;
import fr.epistudio.epysia.assets.loaders.SpriteTilemapAssetLoader;
import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.editor.gl.GlStateSnapshot;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SamplerFilter;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.environment.SkySettings;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.postfx.PostProcessSettings;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.sprite.SpriteRenderSystem;
import fr.epistudio.epysia.render.sprite.TilemapRenderSystem;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.ProjectRenderSetup;
import fr.epistudio.epysia.vfx.VfxRenderSystem;
import fr.epistudio.epysia.window.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public final class EditorScene3DHost {

    private static final PassClear SCENE_CLEAR = PassClear.color(0.10f, 0.11f, 0.13f);

    private final Window window;
    private final Scene scene;
    private final OpenGlRenderBackend backend = new OpenGlRenderBackend();
    private final EditorRenderSurface renderSurface = new EditorRenderSurface();
    private final EpysiaEngine engine;
    private TextureHandle colorTexture;
    private TextureHandle depthTexture;
    private RenderTargetHandle renderTarget;
    private TextureHandle previewColorTexture;
    private TextureHandle previewDepthTexture;
    private RenderTargetHandle previewTarget;
    private int previewWidth;
    private int previewHeight;
    private MeshRenderSystem meshRenderSystem;
    private VfxRenderSystem vfxRenderSystem;
    private SpriteRenderSystem spriteRenderSystem;
    private TilemapRenderSystem tilemapRenderSystem;
    private PostProcessSystem postProcessSystem;
    private ShaderLoader shaderLoader;
    private ShaderWatcher shaderWatcher;
    private int currentWidth;
    private int currentHeight;
    private boolean initialized;

    public EditorScene3DHost(Window window, Scene scene) {
        this.window = window;
        this.scene = scene;
        this.engine = new EpysiaEngine(window, backend);
    }

    public EpysiaEngine engine() {
        return engine;
    }

    public void openScene(Scene scene) {
        engine.addScene(scene);
    }

    public void setActiveScene(Scene scene) {
        engine.setActiveScene(scene);
    }

    public void closeScene(Scene scene) {
        engine.removeScene(scene);
    }

    public OpenGlRenderBackend backend() {
        return backend;
    }

    public Window window() {
        return window;
    }

    public Scene scene() {
        return scene;
    }

    public void initialize(int initialWidth, int initialHeight) {
        if (initialized) {
            return;
        }
        renderSurface.setSize(Math.max(1, initialWidth), Math.max(1, initialHeight));
        backend.initialize(renderSurface);
        engine.addScene(scene);
        loadEngineModules();
        shaderLoader = ShaderLoader.autoDetect();
        shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
        meshRenderSystem = new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger());
        postProcessSystem = new PostProcessSystem(shaderLoader, renderSurface, engine.logger());
        postProcessSystem.setShaderWatcher(shaderWatcher);
        vfxRenderSystem = new VfxRenderSystem(shaderLoader, meshRenderSystem, engine.logger());
        spriteRenderSystem = new SpriteRenderSystem(shaderLoader, meshRenderSystem, engine.logger());
        tilemapRenderSystem = new TilemapRenderSystem(spriteRenderSystem, engine.logger());
        engine.addRenderSystem(meshRenderSystem);
        engine.addRenderSystem(vfxRenderSystem);
        engine.addRenderSystem(spriteRenderSystem);
        engine.addRenderSystem(tilemapRenderSystem);
        engine.addRenderSystem(postProcessSystem);
        engine.initialize();
        BuiltinMeshes builtins = BuiltinMeshes.uploadAll(backend);
        engine.assets().register(new MeshAssetLoader(builtins));
        engine.assets().register(new TextureAssetLoader());
        engine.assets().register(new PhysicsMaterialLoader());
        engine.assets().register(new MaterialAssetLoader());
        engine.assets().register(new ClipAssetLoader());
        engine.assets().register(new ProbesAssetLoader());
        engine.assets().register(new SpriteAtlasAssetLoader());
        engine.assets().register(new SpriteTilemapAssetLoader());
        currentWidth = renderSurface.framebufferWidth();
        currentHeight = renderSurface.framebufferHeight();
        createRenderTarget(currentWidth, currentHeight);
        initialized = true;
    }

    public void applyProjectRenderSetups(List<Class<? extends ProjectRenderSetup>> setups) {
        if (!initialized) {
            return;
        }
        restoreBaselineRenderSystems();
        for (Class<? extends ProjectRenderSetup> setupClass : setups) {
            runRenderSetup(setupClass);
        }
    }

    private void runRenderSetup(Class<? extends ProjectRenderSetup> setupClass) {
        try {
            setupClass.getDeclaredConstructor().newInstance().configure(engine);
            engine.logger().info("[editor] applied render setup " + setupClass.getName());
        } catch (ReflectiveOperationException | RuntimeException error) {
            engine.logger().error("[editor] render setup failed: " + setupClass.getName(), error);
        }
    }

    private void restoreBaselineRenderSystems() {
        List<RenderSystem> current = engine.renderSystems();
        for (RenderSystem system : current) {
            if (system != meshRenderSystem && system != vfxRenderSystem && system != spriteRenderSystem
                    && system != tilemapRenderSystem && system != postProcessSystem) {
                engine.removeRenderSystem(system);
            }
        }
        if (!current.contains(meshRenderSystem)) {
            meshRenderSystem = new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger());
            engine.addRenderSystem(meshRenderSystem);
        }
        if (!current.contains(vfxRenderSystem)) {
            vfxRenderSystem = new VfxRenderSystem(shaderLoader, meshRenderSystem, engine.logger());
            engine.addRenderSystem(vfxRenderSystem);
        }
        if (!current.contains(spriteRenderSystem)) {
            spriteRenderSystem = new SpriteRenderSystem(shaderLoader, meshRenderSystem, engine.logger());
            engine.addRenderSystem(spriteRenderSystem);
        }
        if (!current.contains(tilemapRenderSystem)) {
            tilemapRenderSystem = new TilemapRenderSystem(spriteRenderSystem, engine.logger());
            engine.addRenderSystem(tilemapRenderSystem);
        }
        if (!current.contains(postProcessSystem)) {
            postProcessSystem = new PostProcessSystem(shaderLoader, renderSurface, engine.logger());
            postProcessSystem.setShaderWatcher(shaderWatcher);
            engine.addRenderSystem(postProcessSystem);
        }
    }

    private void loadEngineModules() {
        SystemRegistryImpl registry = new SystemRegistryImpl();
        List<EngineModule> modules = new ArrayList<>();
        for (EngineModule module : ServiceLoader.load(EngineModule.class)) {
            modules.add(module);
        }
        modules.sort(Comparator.comparingInt(EngineModule::order));
        for (EngineModule module : modules) {
            module.registerSystems(registry);
        }
        for (GameSystem system : registry.systems()) {
            engine.addSystem(system);
        }
    }

    public int renderFrame(EditorCamera editorCamera, int desiredWidth, int desiredHeight) {
        return renderFrame(editorCamera, desiredWidth, desiredHeight, Camera3D.CURRENT_STATE_ALPHA);
    }

    public int renderFrame(EditorCamera editorCamera, int desiredWidth, int desiredHeight, float alpha) {
        if (!initialized) {
            return 0;
        }
        int width = Math.max(1, desiredWidth);
        int height = Math.max(1, desiredHeight);
        editorCamera.setAspectRatio((float) width / (float) height);
        return renderWithCamera(editorCamera.camera(), width, height, alpha);
    }

    public int renderFrameFrom(Camera3D camera, int desiredWidth, int desiredHeight) {
        return renderFrameFrom(camera, desiredWidth, desiredHeight, Camera3D.CURRENT_STATE_ALPHA);
    }

    public int renderFrameFrom(Camera3D camera, int desiredWidth, int desiredHeight, float alpha) {
        if (!initialized) {
            return 0;
        }
        int width = Math.max(1, desiredWidth);
        int height = Math.max(1, desiredHeight);
        camera.setAspectRatio((float) width / (float) height);
        return renderWithCamera(camera, width, height, alpha);
    }

    private int renderWithCamera(Camera3D camera, int width, int height, float alpha) {
        ensureTargetSize(width, height);
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            engine.render(List.of(camera), renderTarget, alpha);
            return backend.glTextureName(colorTexture);
        } finally {
            snapshot.restore();
        }
    }

    public int renderPreviewFrom(Camera3D camera, int desiredWidth, int desiredHeight) {
        if (!initialized) {
            return 0;
        }
        int width = Math.max(1, desiredWidth);
        int height = Math.max(1, desiredHeight);
        camera.setAspectRatio((float) width / (float) height);
        ensurePreviewTargetSize(width, height);
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            engine.render(List.of(camera), previewTarget, Camera3D.CURRENT_STATE_ALPHA);
            return backend.glTextureName(previewColorTexture);
        } finally {
            snapshot.restore();
        }
    }

    private void ensurePreviewTargetSize(int width, int height) {
        if (width == previewWidth && height == previewHeight && previewTarget != null) {
            return;
        }
        destroyPreviewTarget();
        previewWidth = width;
        previewHeight = height;
        previewColorTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        previewDepthTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT, SamplerFilter.LINEAR));
        previewTarget = backend.createRenderTarget(new RenderTargetDescriptor(width, height,
                List.of(previewColorTexture), Optional.of(previewDepthTexture)));
    }

    private void destroyPreviewTarget() {
        if (previewTarget != null) {
            backend.destroy(previewTarget);
            previewTarget = null;
        }
        if (previewColorTexture != null) {
            backend.destroy(previewColorTexture);
            previewColorTexture = null;
        }
        if (previewDepthTexture != null) {
            backend.destroy(previewDepthTexture);
            previewDepthTexture = null;
        }
    }

    public Optional<GameObject> pickAt(EditorCamera editorCamera, int x, int y, int width, int height) {
        if (!initialized) {
            return Optional.empty();
        }
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            return engine.pickAt(editorCamera.camera(), x, y, Math.max(1, width), Math.max(1, height));
        } finally {
            snapshot.restore();
        }
    }

    public void shutdown() {
        if (!initialized) {
            return;
        }
        destroyRenderTarget();
        destroyPreviewTarget();
        engine.shutdown();
        backend.shutdown();
        initialized = false;
    }

    private void ensureTargetSize(int width, int height) {
        if (width == currentWidth && height == currentHeight && renderTarget != null) {
            return;
        }
        renderSurface.setSize(width, height);
        engine.onResize(width, height);
        destroyRenderTarget();
        currentWidth = width;
        currentHeight = height;
        createRenderTarget(width, height);
    }

    private void createRenderTarget(int width, int height) {
        colorTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, SamplerFilter.LINEAR));
        depthTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT, SamplerFilter.LINEAR));
        renderTarget = backend.createRenderTarget(new RenderTargetDescriptor(width, height,
                List.of(colorTexture), Optional.of(depthTexture)));
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
    }

    public boolean isInitialized() {
        return initialized;
    }

    public PostProcessSettings postProcessSettings() {
        return postProcessSystem.settings();
    }

    public SkySettings skySettings() {
        return meshRenderSystem.environment().settings();
    }

    public MeshRenderSystem meshRenderSystem() {
        return meshRenderSystem;
    }

    public void notifyShaderFileSaved(Path savedFile) {
        if (shaderWatcher != null) {
            shaderWatcher.notifyFileSaved(savedFile);
        }
    }

    public int currentWidth() {
        return currentWidth;
    }

    public int currentHeight() {
        return currentHeight;
    }
}
