package fr.epistudio.epysia;

import fr.epistudio.epysia.animation.AnimationClock;
import fr.epistudio.epysia.components.transforms.TransformResolver;
import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.concurrent.BackgroundTasks;
import fr.epistudio.epysia.concurrent.MainThread;
import fr.epistudio.epysia.debug.DebugDraw;
import fr.epistudio.epysia.events.EventBus;
import fr.epistudio.epysia.pool.ObjectPools;
import fr.epistudio.epysia.tween.Tweens;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.NetworkReceiveSystem;
import fr.epistudio.epysia.audio.AudioSystem;
import fr.epistudio.epysia.navigation.NavigationService;
import fr.epistudio.epysia.navigation.NavigationSystem;
import fr.epistudio.epysia.net.NetworkService;
import fr.epistudio.epysia.steam.SteamCallbackSystem;
import fr.epistudio.epysia.steam.SteamService;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.Frame;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.SceneTexture;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.mesh.PickingPass;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.input.action.InputActions;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.PreRenderPass;
import fr.epistudio.epysia.render.SceneCapture;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.profiling.FrameProfiler;
import fr.epistudio.epysia.render.postfx.PostEffects;
import fr.epistudio.epysia.render.postfx.PostProcessSettings;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.render.postfx.ScenePostEffects;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.runtime.NullRuntimeChannel;
import fr.epistudio.epysia.runtime.RuntimeChannel;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.SceneLoader;
import fr.epistudio.epysia.scripting.DefaultHud;
import fr.epistudio.epysia.scripting.DefaultScheduler;
import fr.epistudio.epysia.scripting.Hud;
import fr.epistudio.epysia.scripting.Scheduler;
import fr.epistudio.epysia.window.Window;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import fr.epistudio.epysia.render.ProfiledRenderSystem;

public final class EpysiaEngine implements StageConfigurer, EngineServices, SceneCapture {
    private static final float ASSET_SWEEP_INTERVAL_SECONDS = 5.0f;
    private static final int DEFAULT_UPLOADS_PER_TICK = 4;
    private static final int MAXIMUM_CATCH_UP_STEPS = 4;

    private PassClear defaultClear = PassClear.color(0.10f, 0.12f, 0.18f);
    private final InputActions inputActions = InputActions.defaults();

    private final Window window;
    private final RenderBackend renderBackend;
    private final List<Scene> scenes = new ArrayList<>();
    private Scene activeScene;
    private final List<GameSystem> gameSystems = new ArrayList<>();
    private final List<String> gameSystemSectionNames = new ArrayList<>();
    private final SystemRegistryImpl systemRegistry = new SystemRegistryImpl();
    private final List<RenderSystem> renderSystems = new ArrayList<>();
    private final List<PreRenderPass> preRenderPasses = new ArrayList<>();
    private boolean capturing;
    private final List<String> renderSystemSectionNames = new ArrayList<>();
    private final Frame frame = new Frame();
    private final Map<RenderPass, StageBinding> stageBindings = defaultStageBindings();
    private final Map<RenderPass, List<Runnable>> stagePreparations = new HashMap<>();
    private final Map<RenderPass, RenderPass> stageFollowers = new HashMap<>();
    private final Map<SceneTexture, TextureHandle> sceneTextures = new EnumMap<>(SceneTexture.class);
    private final AssetRegistry assetRegistry = new AssetRegistry(this);
    private final FrameProfiler profiler = new FrameProfiler();
    private final DebugDraw debugDraw = new DebugDraw();
    private final EventBus eventBus = new EventBus();
    private final ObjectPools objectPools = new ObjectPools(this);
    private final Tweens tweens = new Tweens();
    private SceneLoader sceneLoader;
    private final AnimationClock animationClock = new AnimationClock();
    private final TransformResolver transformResolver = new TransformResolver();
    private final DefaultScheduler scheduler = new DefaultScheduler();
    private final DefaultHud hud = new DefaultHud();
    private final PostProcessSettings detachedPostProcessSettings = new PostProcessSettings();
    private final PostEffects postEffectsAccess = new ScenePostEffects(this::scene, this::resolvePostProcessSettings);
    private NetworkService networkService;
    private NavigationService navigationService;
    private SteamService steamService;
    private RuntimeChannel runtimeChannel = new NullRuntimeChannel();
    private Logger logger = new ConsoleLogger();
    private FontRegistry fontRegistry;
    private PickingPass pickingPass;
    private volatile boolean shutdownRequested;
    private boolean initialized;
    private boolean playing;
    private float secondsSinceAssetSweep;
    private int uploadsPerTick = DEFAULT_UPLOADS_PER_TICK;
    private int pendingCatchUpSteps;
    private final BackgroundTasks backgroundTasks = new BackgroundTasks(this::logger);

    public EpysiaEngine(Window window, RenderBackend renderBackend) {
        this.window = window;
        this.renderBackend = renderBackend;
    }

    public void addScene(Scene scene) {
        scenes.add(scene);
        scene.setRemovalListener(this::dispatchDestruction);
        if (activeScene == null) {
            activeScene = scene;
        }
    }

    public void setActiveScene(Scene scene) {
        if (!scenes.contains(scene)) {
            scenes.add(scene);
        }
        activeScene = scene;
        scene.setRemovalListener(this::dispatchDestruction);
    }

    private void dispatchDestruction(GameObject removed) {
        invokeLifecycle(removed, component -> component.onDestroy(this), "onDestroy");
    }

    public void removeScene(Scene scene) {
        scenes.remove(scene);
        if (activeScene == scene) {
            activeScene = scenes.isEmpty() ? null : scenes.get(0);
        }
    }

    public void addSystem(GameSystem system) {
        if (initialized) {
            system.initialize(this);
        }
        gameSystems.add(system);
        gameSystemSectionNames.add(FrameProfiler.SYSTEM_PREFIX + system.getClass().getSimpleName());
        systemRegistry.add(system);
    }

    public void addRenderSystem(RenderSystem renderSystem) {
        if (renderSystem instanceof ProfiledRenderSystem profiled) {
            profiled.setProfiler(profiler);
        }
        if (initialized) {
            renderSystem.initialize(renderBackend, this);
        }
        renderSystems.add(renderSystem);
        renderSystemSectionNames.add(FrameProfiler.COLLECT_PREFIX + renderSystem.getClass().getSimpleName());
        if (renderSystem instanceof PostProcessSystem postProcess) {
            postProcess.settings().copyFrom(detachedPostProcessSettings);
        }
    }

    @Override
    public void removeRenderSystem(RenderSystem renderSystem) {
        int index = renderSystems.indexOf(renderSystem);
        if (index < 0) {
            return;
        }
        renderSystems.remove(index);
        renderSystemSectionNames.remove(index);
        if (initialized) {
            renderSystem.shutdown(renderBackend);
        }
    }

    public List<RenderSystem> renderSystems() {
        return List.copyOf(renderSystems);
    }

    public <T extends RenderSystem> T renderSystem(Class<T> type) {
        for (RenderSystem system : renderSystems) {
            if (type.isInstance(system)) {
                return type.cast(system);
            }
        }
        throw new EpysiaException("No render system registered for " + type.getName());
    }

    public boolean hasRenderSystem(Class<? extends RenderSystem> type) {
        for (RenderSystem system : renderSystems) {
            if (type.isInstance(system)) {
                return true;
            }
        }
        return false;
    }

    public void setLogger(Logger logger) {
        if (logger != null) {
            this.logger = logger;
            scheduler.setLogger(logger);
        }
    }

    public void setRuntimeChannel(RuntimeChannel channel) {
        if (channel != null) {
            this.runtimeChannel = channel;
        }
    }

    public void setDefaultClearColor(PassClear clear) {
        if (clear != null) {
            this.defaultClear = clear;
            rebindScreenStagesToDefaultClear();
        }
    }

    private void rebindScreenStagesToDefaultClear() {
        for (Map.Entry<RenderPass, StageBinding> entry : new ArrayList<>(stageBindings.entrySet())) {
            StageBinding binding = entry.getValue();
            if (binding.target().id() == RenderTargetHandle.SCREEN.id()
                    && !binding.clear().equals(PassClear.none())) {
                stageBindings.put(entry.getKey(), new StageBinding(binding.target(), defaultClear));
            }
        }
    }

    public void initialize() {
        MainThread.adopt();
        fontRegistry = new FontRegistry(renderBackend);
        fontRegistry.load(FontRegistry.DEFAULT_NAME, "fonts/AdwaitaMono-Regular.ttf", 24.0f);
        for (RenderSystem system : renderSystems) {
            system.initialize(renderBackend, this);
        }
        for (GameSystem system : gameSystems) {
            system.initialize(this);
        }
        initialized = true;
    }

    public void tick(InputState input, float deltaTimeSeconds) {
        profiler.begin(FrameProfiler.TICK_SECTION);
        hud.clear();
        profiler.begin(FrameProfiler.BACKGROUND_DELIVERY_SECTION);
        backgroundTasks.deliverCompleted();
        profiler.end();
        assetRegistry.drainReadyUploads(uploadsPerTick);
        scheduler.tick(deltaTimeSeconds);
        if (activeScene != null) {
            advanceActiveScene(input, deltaTimeSeconds);
        }
        sweepUnusedAssets(deltaTimeSeconds);
        profiler.end();
    }

    private void advanceActiveScene(InputState input, float deltaTimeSeconds) {
        applyPendingSceneLoads();
        activeScene.advanceTick();
        dispatchDeactivations(activeScene);
        dispatchActivations(activeScene);
        captureTransformInterpolationSnapshots(activeScene);
        animationClock.advance(activeScene, deltaTimeSeconds);
        debugDraw.advance(deltaTimeSeconds);
        eventBus.deliverDeferred();
        tweens.advance(deltaTimeSeconds);
        updateGameSystems(input, deltaTimeSeconds);
    }

    public int uploadsPerTick() {
        return uploadsPerTick;
    }

    public void setUploadsPerTick(int value) {
        uploadsPerTick = Math.max(1, value);
    }

    private void sweepUnusedAssets(float deltaTimeSeconds) {
        secondsSinceAssetSweep += deltaTimeSeconds;
        if (secondsSinceAssetSweep < ASSET_SWEEP_INTERVAL_SECONDS) {
            return;
        }
        secondsSinceAssetSweep = 0.0f;
        assetRegistry.unloadUnused();
    }

    public boolean isPlaying() {
        return playing;
    }

    public <T extends GameSystem> Optional<T> gameSystem(Class<T> type) {
        for (GameSystem system : gameSystems) {
            if (type.isInstance(system)) {
                return Optional.of(type.cast(system));
            }
        }
        return Optional.empty();
    }

    public void beginPlay() {
        playing = true;
        if (activeScene == null) {
            return;
        }
        for (GameObject gameObject : activeScene.gameObjects()) {
            invokeLifecycle(gameObject, component -> component.onPlayStart(this), "onPlayStart");
        }
    }

    public void endPlay() {
        if (activeScene != null) {
            for (GameObject gameObject : activeScene.gameObjects()) {
                invokeLifecycle(gameObject, component -> component.onPlayStop(this), "onPlayStop");
            }
        }
        playing = false;
    }

    private void dispatchActivations(Scene scene) {
        for (GameObject gameObject : scene.drainRecentlyActivated()) {
            invokeLifecycle(gameObject, component -> component.onLoad(this), "onLoad");
            if (playing) {
                invokeLifecycle(gameObject, component -> component.onPlayStart(this), "onPlayStart");
            }
        }
    }

    private void dispatchDeactivations(Scene scene) {
        for (GameObject gameObject : scene.drainRecentlyDeactivated()) {
            if (playing) {
                invokeLifecycle(gameObject, component -> component.onPlayStop(this), "onPlayStop");
            }
        }
    }

    private void invokeLifecycle(GameObject gameObject, Consumer<IComponent> hook, String hookName) {
        for (IComponent component : new ArrayList<>(gameObject.components())) {
            try {
                hook.accept(component);
            } catch (RuntimeException error) {
                logger.error("[EpysiaEngine] " + hookName + " failed for "
                        + component.getClass().getName(), error);
            }
        }
    }

    private void updateGameSystems(InputState input, float deltaTimeSeconds) {
        for (int index = 0; index < gameSystems.size(); index++) {
            profiler.begin(gameSystemSectionNames.get(index));
            gameSystems.get(index).update(activeScene, input, deltaTimeSeconds);
            profiler.end();
        }
        lateUpdateGameSystems(input, deltaTimeSeconds);
    }

    private void lateUpdateGameSystems(InputState input, float deltaTimeSeconds) {
        for (GameSystem system : gameSystems) {
            system.lateUpdate(activeScene, input, deltaTimeSeconds);
        }
    }

    public FrameProfiler profiler() {
        return profiler;
    }

    private void captureTransformInterpolationSnapshots(Scene scene) {
        for (Transform3D transform : scene.componentsOf(Transform3D.class)) {
            transform.captureInterpolationSnapshot();
        }
    }

    public Optional<GameObject> pickAt(Camera3D camera, int x, int y, int width, int height) {
        if (camera == null || width <= 0 || height <= 0) {
            return Optional.empty();
        }
        if (pickingPass == null) {
            pickingPass = new PickingPass(ShaderLoader.autoDetect(), logger);
        }
        if (activeScene == null) {
            return Optional.empty();
        }
        return pickingPass.pickAt(activeScene, camera, x, y, width, height, renderBackend);
    }

    @Override
    public void addPreRenderPass(PreRenderPass pass) {
        preRenderPasses.add(pass);
    }

    @Override
    public void removePreRenderPass(PreRenderPass pass) {
        preRenderPasses.remove(pass);
    }

    private void resolveTransforms(float interpolationAlpha) {
        if (activeScene == null) {
            return;
        }
        long start = System.nanoTime();
        transformResolver.resolve(activeScene.componentsOf(Transform3D.class), interpolationAlpha);
        profiler.record("transforms/resolve", System.nanoTime() - start);
    }

    public void advanceAnimators(float deltaSeconds) {
        if (activeScene != null) {
            animationClock.advance(activeScene, deltaSeconds);
        }
    }

    @Override
    public void renderTo(List<Camera3D> cameras, RenderTargetHandle target, float interpolationAlpha) {
        render(cameras, target, interpolationAlpha);
    }

    private void runPreRenderPasses(float interpolationAlpha) {
        if (capturing || preRenderPasses.isEmpty()) {
            return;
        }
        capturing = true;
        try {
            for (int index = 0; index < preRenderPasses.size(); index++) {
                preRenderPasses.get(index).capture(this, interpolationAlpha);
            }
        } finally {
            capturing = false;
        }
    }

    public void render(List<Camera3D> activeCameras, RenderTargetHandle screenTarget, float interpolationAlpha) {
        runPreRenderPasses(interpolationAlpha);
        profiler.begin(FrameProfiler.RENDER_SECTION);
        resolveTransforms(interpolationAlpha);
        frame.reset();
        renderBackend.beginFrame();
        collectRenderSystems(RenderContext.of(activeCameras, screenTarget, interpolationAlpha,
                animationClock.generation()));
        profiler.begin(FrameProfiler.DRAIN_SECTION);
        drainStages(screenTarget);
        renderBackend.endFrame();
        profiler.end();
        profiler.end();
        profiler.publishFrame();
    }

    private void collectRenderSystems(RenderContext context) {
        if (activeScene == null) {
            return;
        }
        profiler.begin(FrameProfiler.COLLECT_SECTION);
        for (int index = 0; index < renderSystems.size(); index++) {
            profiler.begin(renderSystemSectionNames.get(index));
            renderSystems.get(index).collect(activeScene, frame, context);
            profiler.end();
        }
        profiler.end();
    }

    public void onResize(int width, int height) {
        renderBackend.onViewportResize(width, height);
        for (RenderSystem system : renderSystems) {
            system.onResize(renderBackend, this, width, height);
        }
    }

    public void shutdown() {
        backgroundTasks.shutdown();
        for (int index = gameSystems.size() - 1; index >= 0; index--) {
            gameSystems.get(index).shutdown();
        }
        for (int index = renderSystems.size() - 1; index >= 0; index--) {
            renderSystems.get(index).shutdown(renderBackend);
        }
        if (pickingPass != null) {
            pickingPass.shutdown();
            pickingPass = null;
        }
        if (fontRegistry != null) {
            fontRegistry.destroyAll();
        }
        assetRegistry.clear();
        runtimeChannel.close();
        initialized = false;
    }

    public void requestShutdown() {
        shutdownRequested = true;
    }

    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    @Override
    public void bindStageTarget(RenderPass pass, RenderTargetHandle target, PassClear clear) {
        stageBindings.put(pass, new StageBinding(target, clear));
    }

    @Override
    public void publishSceneTexture(SceneTexture slot, TextureHandle texture) {
        sceneTextures.put(slot, texture);
    }

    @Override
    public Optional<TextureHandle> sceneTexture(SceneTexture slot) {
        return Optional.ofNullable(sceneTextures.get(slot));
    }

    @Override
    public void bindStagePreparation(RenderPass pass, Runnable preparation) {
        stagePreparations.computeIfAbsent(pass, ignored -> new ArrayList<>()).add(preparation);
    }

    @Override
    public void requestCatchUpSteps(int steps) {
        pendingCatchUpSteps = Math.clamp(pendingCatchUpSteps + steps,
                -MAXIMUM_CATCH_UP_STEPS, MAXIMUM_CATCH_UP_STEPS);
    }

    public int consumeCatchUpSteps() {
        int steps = pendingCatchUpSteps;
        pendingCatchUpSteps = 0;
        return steps;
    }

    @Override
    public InputActions inputActions() {
        return inputActions;
    }

    @Override
    public Window window() {
        return window;
    }

    @Override
    public RenderBackend renderBackend() {
        return renderBackend;
    }

    @Override
    public FontRegistry fonts() {
        return fontRegistry;
    }

    @Override
    public Scene scene() {
        if (activeScene == null) {
            throw new EpysiaException(
                    "EngineServices.scene() requested but no scene registered with the engine.");
        }
        return activeScene;
    }

    @Override
    public SystemRegistry systems() {
        return systemRegistry;
    }

    @Override
    public AssetRegistry assets() {
        return assetRegistry;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public BackgroundTasks backgroundTasks() {
        return backgroundTasks;
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public Hud hud() {
        return hud;
    }

    @Override
    public PostEffects postEffects() {
        return postEffectsAccess;
    }

    @Override
    public DebugDraw debug() {
        return debugDraw;
    }

    @Override
    public EventBus events() {
        return eventBus;
    }

    @Override
    public ObjectPools pools() {
        return objectPools;
    }

    @Override
    public Tweens tweens() {
        return tweens;
    }

    @Override
    public Optional<SceneLoader> scenes() {
        return Optional.ofNullable(sceneLoader);
    }

    public void setSceneLoader(SceneLoader loader) {
        this.sceneLoader = loader;
    }

    private void applyPendingSceneLoads() {
        if (sceneLoader != null && sceneLoader.hasPendingWork()) {
            sceneLoader.applyPending();
        }
    }

    @Override
    public SteamService steam() {
        if (steamService == null) {
            steamService = resolveSteamService();
        }
        return steamService;
    }

    private SteamService resolveSteamService() {
        for (GameSystem system : gameSystems) {
            if (system instanceof SteamCallbackSystem steam) {
                return steam.service();
            }
        }
        return SteamService.detached();
    }

    @Override
    public NetworkService network() {
        if (networkService == null) {
            networkService = resolveNetworkService();
        }
        return networkService;
    }

    @Override
    public Optional<AudioSystem> audio() {
        for (GameSystem system : gameSystems) {
            if (system instanceof AudioSystem audioSystem) {
                return Optional.of(audioSystem);
            }
        }
        return Optional.empty();
    }

    @Override
    public NavigationService navigation() {
        if (navigationService == null) {
            navigationService = resolveNavigationService();
        }
        return navigationService;
    }

    private NavigationService resolveNavigationService() {
        for (GameSystem system : gameSystems) {
            if (system instanceof NavigationSystem navigation) {
                return navigation.service();
            }
        }
        NavigationSystem fallback = new NavigationSystem();
        addSystem(fallback);
        return fallback.service();
    }

    private NetworkService resolveNetworkService() {
        for (GameSystem system : gameSystems) {
            if (system instanceof NetworkReceiveSystem network) {
                return network.service();
            }
        }
        NetworkReceiveSystem fallback = new NetworkReceiveSystem();
        addSystem(fallback);
        return fallback.service();
    }

    private PostProcessSettings resolvePostProcessSettings() {
        Scene scene = activeScene;
        if (scene != null) {
            return scene.postProcess();
        }
        for (RenderSystem system : renderSystems) {
            if (system instanceof PostProcessSystem postProcess) {
                return postProcess.settings();
            }
        }
        return detachedPostProcessSettings;
    }

    public DefaultHud hudEntries() {
        return hud;
    }

    public RuntimeChannel runtimeChannel() {
        return runtimeChannel;
    }

    private void drainStages(RenderTargetHandle screenTarget) {
        boolean screenWasOpened = false;
        for (RenderPass pass : RenderPasses.ordered()) {
            List<DrawCommand> commands = frame.commandsFor(pass);
            if (commands.isEmpty()) {
                continue;
            }
            runStagePreparations(pass);
            frame.sortByKey(pass);
            StageBinding binding = effectiveBinding(pass, screenTarget);
            renderBackend.beginProfileSection(pass.name());
            renderBackend.beginPass(binding.target(), binding.clear());
            for (DrawCommand command : commands) {
                renderBackend.execute(command);
            }
            renderBackend.endPass();
            renderBackend.endProfileSection();
            if (binding.target().id() == screenTarget.id()) {
                screenWasOpened = true;
            }
        }
        if (!screenWasOpened) {
            renderBackend.beginPass(screenTarget, defaultClear);
            renderBackend.endPass();
        }
    }

    private void runStagePreparations(RenderPass pass) {
        List<Runnable> preparations = stagePreparations.get(pass);
        if (preparations == null) {
            return;
        }
        for (int i = 0; i < preparations.size(); i++) {
            preparations.get(i).run();
        }
    }

    private StageBinding effectiveBinding(RenderPass pass, RenderTargetHandle screenTarget) {
        StageBinding configured = resolveFollowedBinding(pass);
        if (configured.target().id() == RenderTargetHandle.SCREEN.id() && screenTarget.id() != RenderTargetHandle.SCREEN.id()) {
            return new StageBinding(screenTarget, configured.clear());
        }
        return configured;
    }

    private StageBinding resolveFollowedBinding(RenderPass pass) {
        StageBinding configured = bindingOrDefault(pass);
        RenderPass followed = stageFollowers.get(pass);
        if (followed == null) {
            return configured;
        }
        return new StageBinding(bindingOrDefault(followed).target(), configured.clear());
    }

    private StageBinding bindingOrDefault(RenderPass pass) {
        StageBinding configured = stageBindings.get(pass);
        if (configured != null) {
            return configured;
        }
        return new StageBinding(RenderTargetHandle.SCREEN, PassClear.none());
    }

    @Override
    public void bindStageTargetFollowing(RenderPass pass, RenderPass followed, PassClear clear) {
        stageFollowers.put(pass, followed);
        stageBindings.put(pass, new StageBinding(RenderTargetHandle.SCREEN, clear));
    }

    private Map<RenderPass, StageBinding> defaultStageBindings() {
        Map<RenderPass, StageBinding> bindings = new HashMap<>();
        StageBinding screenWithClear = new StageBinding(RenderTargetHandle.SCREEN, defaultClear);
        StageBinding screenNoClear = new StageBinding(RenderTargetHandle.SCREEN, PassClear.none());
        bindings.put(RenderPasses.PRE_3D, screenWithClear);
        bindings.put(RenderPasses.OPAQUE_3D, screenWithClear);
        bindings.put(RenderPasses.VIEW_MODEL_3D, new StageBinding(RenderTargetHandle.SCREEN, PassClear.depthOnly()));
        bindings.put(RenderPasses.TRANSPARENT_3D, screenNoClear);
        bindings.put(RenderPasses.WORLD_2D, screenNoClear);
        bindings.put(RenderPasses.OVERLAY_2D, screenNoClear);
        bindings.put(RenderPasses.UI, screenNoClear);
        bindings.put(RenderPasses.POST, screenNoClear);
        return bindings;
    }

    private record StageBinding(RenderTargetHandle target, PassClear clear) {
    }
}
