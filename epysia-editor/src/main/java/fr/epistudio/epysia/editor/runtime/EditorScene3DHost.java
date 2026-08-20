package fr.epistudio.epysia.editor.runtime;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.GameSystem;
import fr.epistudio.epysia.SystemRegistryImpl;
import fr.epistudio.epysia.assets.loaders.ClipAssetLoader;
import fr.epistudio.epysia.assets.loaders.MaterialAssetLoader;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.assets.loaders.AudioBufferLoaderAsset;
import fr.epistudio.epysia.assets.loaders.PhysicsMaterialLoader;
import fr.epistudio.epysia.assets.loaders.InstancesAssetLoader;
import fr.epistudio.epysia.assets.loaders.ProbesAssetLoader;
import fr.epistudio.epysia.assets.loaders.SpriteAtlasAssetLoader;
import fr.epistudio.epysia.assets.loaders.SpriteTilemapAssetLoader;
import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import fr.epistudio.epysia.assets.procedural.CurveTextureLoader;
import fr.epistudio.epysia.assets.procedural.GradientTextureLoader;
import fr.epistudio.epysia.assets.procedural.NoiseTextureLoader;
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
import fr.epistudio.epysia.render.lighting.ProbeRefreshSystem;
import fr.epistudio.epysia.render.mesh.SilhouettePass;
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
import fr.epistudio.epysia.render.decal.DecalRenderSystem;
import fr.epistudio.epysia.render.volumetric.VolumetricRenderSystem;
import fr.epistudio.epysia.vfx.VfxRenderSystem;
import fr.epistudio.epysia.render.text.TextRenderSystem;
import fr.epistudio.epysia.render.debug.DebugLineRenderSystem;
import fr.epistudio.epysia.render.text.WorldTextRenderSystem;
import fr.epistudio.epysia.ui.UiInputSystem;
import fr.epistudio.epysia.ui.UiRenderSystem;
import fr.epistudio.epysia.window.Window;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public final class EditorScene3DHost {

    private SamplerFilter appliedColorFilter = SamplerFilter.LINEAR;

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
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
    private TextRenderSystem textRenderSystem;
    private WorldTextRenderSystem worldTextRenderSystem;
    private UiRenderSystem uiRenderSystem;
    private final List<RenderSystem> baselineSystems = new ArrayList<>();
    private ShaderLoader shaderLoader;
    private ShaderWatcher shaderWatcher;
    private int currentWidth;
    private int currentHeight;
    private boolean initialized;
    private final long[] frameStepNanos = new long[5];
    private final ViewportRedrawTracker redrawTracker = new ViewportRedrawTracker();
    private final ViewportRedrawTracker previewRedrawTracker = new ViewportRedrawTracker();
    private int lastViewportTexture;
    private int lastPreviewTexture;
    private long skippedFrames;

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
        warmUpPipelines(scene);
    }

    private void warmUpPipelines(Scene scene) {
        if (meshRenderSystem == null) {
            return;
        }
        long start = System.nanoTime();
        long compiledBefore = backend.shaderCompileNanos();
        int warmed = meshRenderSystem.warmUpPipelines(scene);
        long compileMillis = (backend.shaderCompileNanos() - compiledBefore) / NANOS_PER_MILLISECOND;
        if (compileMillis > 0L) {
            engine.logger().info("Pipeline warm-up: " + warmed + " renderers, "
                    + (System.nanoTime() - start) / NANOS_PER_MILLISECOND + " ms, of which "
                    + compileMillis + " ms compiling shaders.");
        }
    }

    public void closeScene(Scene scene) {
        engine.removeScene(scene);
    }

    public SilhouettePass.JointPaletteSource jointPalettes() {
        return meshRenderSystem == null
                ? SilhouettePass.JointPaletteSource.NONE
                : meshRenderSystem::jointPaletteBinding;
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
        shaderLoader.useProject(() -> engine.assets().locator());
        shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
        meshRenderSystem = new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger());
        postProcessSystem = new PostProcessSystem(shaderLoader, renderSurface, engine.logger());
        postProcessSystem.setShaderWatcher(shaderWatcher);
        vfxRenderSystem = new VfxRenderSystem(shaderLoader, meshRenderSystem, engine.logger());
        vfxRenderSystem.useProject(() -> engine.assets().locator());
        spriteRenderSystem = new SpriteRenderSystem(shaderLoader, shaderWatcher, meshRenderSystem, engine.logger());
        tilemapRenderSystem = new TilemapRenderSystem(spriteRenderSystem, engine.logger());
        textRenderSystem = new TextRenderSystem(shaderLoader, renderSurface, engine, engine.logger());
        worldTextRenderSystem = new WorldTextRenderSystem(shaderLoader, meshRenderSystem, engine.debug());
        uiRenderSystem = new UiRenderSystem(shaderLoader, renderSurface, engine);
        engine.addRenderSystem(meshRenderSystem);
        engine.addRenderSystem(vfxRenderSystem);
        engine.addRenderSystem(new VolumetricRenderSystem(shaderLoader, renderSurface, engine.logger()));
        engine.addRenderSystem(new DecalRenderSystem(shaderLoader, engine.logger()));
        engine.addRenderSystem(spriteRenderSystem);
        engine.addRenderSystem(tilemapRenderSystem);
        engine.addRenderSystem(postProcessSystem);
        engine.addRenderSystem(worldTextRenderSystem);
        engine.addRenderSystem(new DebugLineRenderSystem(shaderLoader, meshRenderSystem, engine.debug()));
        engine.addRenderSystem(textRenderSystem);
        engine.addRenderSystem(uiRenderSystem);
        baselineSystems.addAll(engine.renderSystems());
        engine.initialize();
        BuiltinMeshes builtins = BuiltinMeshes.uploadAll(backend);
        engine.assets().register(new MeshAssetLoader(builtins));
        engine.assets().register(new TextureAssetLoader());
        engine.assets().register(new NoiseTextureLoader());
        engine.assets().register(new GradientTextureLoader());
        engine.assets().register(new CurveTextureLoader());
        engine.assets().register(new PhysicsMaterialLoader());
        engine.assets().register(new AudioBufferLoaderAsset());
        engine.assets().register(new MaterialAssetLoader());
        engine.assets().register(new ClipAssetLoader());
        engine.assets().register(new ProbesAssetLoader());
        engine.assets().register(new InstancesAssetLoader());
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
        List<RenderSystem> current = List.copyOf(engine.renderSystems());
        for (RenderSystem system : current) {
            if (!baselineSystems.contains(system)) {
                engine.removeRenderSystem(system);
            }
        }
        if (!current.contains(meshRenderSystem)) {
            meshRenderSystem = new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger());
            engine.addRenderSystem(meshRenderSystem);
        }
        if (!current.contains(vfxRenderSystem)) {
            vfxRenderSystem = new VfxRenderSystem(shaderLoader, meshRenderSystem, engine.logger());
        vfxRenderSystem.useProject(() -> engine.assets().locator());
            engine.addRenderSystem(vfxRenderSystem);
        }
        if (!current.contains(spriteRenderSystem)) {
            spriteRenderSystem = new SpriteRenderSystem(shaderLoader, shaderWatcher, meshRenderSystem, engine.logger());
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
        if (!current.contains(worldTextRenderSystem)) {
            worldTextRenderSystem = new WorldTextRenderSystem(shaderLoader, meshRenderSystem, engine.debug());
            engine.addRenderSystem(worldTextRenderSystem);
        }
        if (!current.contains(textRenderSystem)) {
            textRenderSystem = new TextRenderSystem(shaderLoader, renderSurface, engine, engine.logger());
            engine.addRenderSystem(textRenderSystem);
        }
        if (!engine.renderSystems().contains(uiRenderSystem)) {
            uiRenderSystem = new UiRenderSystem(shaderLoader, renderSurface, engine);
            engine.addRenderSystem(uiRenderSystem);
        }
    }

    private void loadEngineModules() {
        engine.addSystem(new UiInputSystem());
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
        postProcessSystem.setStretchEnabled(false);
        if (!redrawTracker.shouldRedraw(editorCamera.camera(), scene, width, height, engine.isPlaying())) {
            skippedFrames++;
            return lastViewportTexture;
        }
        long probeStart = System.nanoTime();
        refreshProbesWhileEditing(editorCamera.camera().position(new Vector3f()));
        frameStepNanos[4] = System.nanoTime() - probeStart;
        lastViewportTexture = renderWithCamera(editorCamera.camera(), width, height, alpha);
        return lastViewportTexture;
    }

    private void refreshProbesWhileEditing(Vector3f viewerPosition) {
        if (engine.isPlaying()) {
            return;
        }
        engine.gameSystem(ProbeRefreshSystem.class)
                .ifPresent(system -> refreshProbes(system, viewerPosition));
    }

    private void refreshProbes(ProbeRefreshSystem system, Vector3f viewerPosition) {
        if (!system.hasWork(engine.scene())) {
            return;
        }
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            system.refresh(engine.scene(), viewerPosition);
        } finally {
            snapshot.restore();
        }
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
        postProcessSystem.setStretchEnabled(true);
        return renderWithCamera(camera, width, height, alpha);
    }

    private int renderWithCamera(Camera3D camera, int width, int height, float alpha) {
        long resizeStart = System.nanoTime();
        ensureTargetSize(width, height);
        long captureStart = System.nanoTime();
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        long renderStart = System.nanoTime();
        try {
            engine.render(List.of(camera), renderTarget, alpha);
            return backend.glTextureName(colorTexture);
        } finally {
            long restoreStart = System.nanoTime();
            snapshot.restore();
            recordFrameSteps(resizeStart, captureStart, renderStart, restoreStart);
        }
    }

    private void recordFrameSteps(long resizeStart, long captureStart, long renderStart,
                                  long restoreStart) {
        frameStepNanos[0] = captureStart - resizeStart;
        frameStepNanos[1] = renderStart - captureStart;
        frameStepNanos[2] = restoreStart - renderStart;
        frameStepNanos[3] = System.nanoTime() - restoreStart;
    }

    public void advanceAnimation(float deltaSeconds) {
        if (!initialized) {
            return;
        }
        engine.advanceAnimators(deltaSeconds);
    }

    public void requestViewportRedraw() {
        redrawTracker.requestRedraw();
        previewRedrawTracker.requestRedraw();
    }

    public long skippedFrames() {
        return skippedFrames;
    }

    public int shaderWatcherListenerCount() {
        return shaderWatcher == null ? 0 : shaderWatcher.listenerCount();
    }

    public long[] frameStepNanos() {
        return frameStepNanos.clone();
    }

    public int renderPreviewFrom(Camera3D camera, int desiredWidth, int desiredHeight) {
        if (!initialized) {
            return 0;
        }
        int width = Math.max(1, desiredWidth);
        int height = Math.max(1, desiredHeight);
        camera.setAspectRatio((float) width / (float) height);
        if (!previewRedrawTracker.shouldRedraw(camera, scene, width, height, engine.isPlaying())) {
            return lastPreviewTexture;
        }
        ensurePreviewTargetSize(width, height);
        GlStateSnapshot snapshot = GlStateSnapshot.capture();
        try {
            engine.render(List.of(camera), previewTarget, Camera3D.CURRENT_STATE_ALPHA);
            lastPreviewTexture = backend.glTextureName(previewColorTexture);
            return lastPreviewTexture;
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
            refreshColorFilter();
            return;
        }
        renderSurface.setSize(width, height);
        engine.onResize(width, height);
        destroyRenderTarget();
        currentWidth = width;
        currentHeight = height;
        createRenderTarget(width, height);
    }

    private void refreshColorFilter() {
        SamplerFilter desired = desiredColorFilter();
        if (colorTexture == null || desired == appliedColorFilter) {
            return;
        }
        appliedColorFilter = desired;
        backend.updateTextureFilter(colorTexture, desired);
    }

    private SamplerFilter desiredColorFilter() {
        return postProcessSettings().pixelPerfectEnabled() ? SamplerFilter.NEAREST : SamplerFilter.LINEAR;
    }

    private void createRenderTarget(int width, int height) {
        appliedColorFilter = desiredColorFilter();
        colorTexture = backend.createTexture(new TextureDescriptor(width, height,
                TextureFormat.RGBA8, TextureUsage.SAMPLED, appliedColorFilter));
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
