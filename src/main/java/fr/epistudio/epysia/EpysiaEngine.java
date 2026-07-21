package fr.epistudio.epysia;

import fr.epistudio.epysia.assets.AssetRegistry;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.Frame;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.RenderPass;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.mesh.PickingPass;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.profiling.FrameProfiler;
import fr.epistudio.epysia.render.postfx.PostEffects;
import fr.epistudio.epysia.render.postfx.PostProcessSettings;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.render.postfx.ScenePostEffects;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.runtime.NullRuntimeChannel;
import fr.epistudio.epysia.runtime.RuntimeChannel;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scripting.DefaultHud;
import fr.epistudio.epysia.scripting.DefaultScheduler;
import fr.epistudio.epysia.scripting.Hud;
import fr.epistudio.epysia.scripting.Scheduler;
import fr.epistudio.epysia.window.Window;

import java.util.ArrayList;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EpysiaEngine implements StageConfigurer, EngineServices {

    private PassClear defaultClear = PassClear.color(0.10f, 0.12f, 0.18f);

    private final Window window;
    private final RenderBackend renderBackend;
    private final List<Scene> scenes = new ArrayList<>();
    private Scene activeScene;
    private final List<GameSystem> gameSystems = new ArrayList<>();
    private final SystemRegistryImpl systemRegistry = new SystemRegistryImpl();
    private final List<RenderSystem> renderSystems = new ArrayList<>();
    private final Frame frame = new Frame();
    private final Map<RenderPass, StageBinding> stageBindings = defaultStageBindings();
    private final Map<RenderPass, List<Runnable>> stagePreparations = new HashMap<>();
    private final Map<RenderPass, RenderPass> stageFollowers = new HashMap<>();
    private final AssetRegistry assetRegistry = new AssetRegistry(this);
    private final long[] cpuTimingsNanosArray = new long[CpuTimings.values().length];
    private final FrameProfiler profiler = new FrameProfiler();
    private final DefaultScheduler scheduler = new DefaultScheduler();
    private final DefaultHud hud = new DefaultHud();
    private final PostProcessSettings detachedPostProcessSettings = new PostProcessSettings();
    private final PostEffects postEffectsAccess = new ScenePostEffects(this::scene, this::resolvePostProcessSettings);
    private RuntimeChannel runtimeChannel = new NullRuntimeChannel();
    private Logger logger = new ConsoleLogger();
    private FontRegistry fontRegistry;
    private PickingPass pickingPass;
    private volatile boolean shutdownRequested;
    private boolean initialized;

    public EpysiaEngine(Window window, RenderBackend renderBackend) {
        this.window = window;
        this.renderBackend = renderBackend;
    }

    public void addScene(Scene scene) {
        scenes.add(scene);
        if (activeScene == null) {
            activeScene = scene;
        }
    }

    public void setActiveScene(Scene scene) {
        if (!scenes.contains(scene)) {
            scenes.add(scene);
        }
        activeScene = scene;
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
        systemRegistry.add(system);
    }

    public void addRenderSystem(RenderSystem renderSystem) {
        if (renderSystem instanceof fr.epistudio.epysia.render.ProfiledRenderSystem profiled) {
            profiled.setProfiler(profiler);
        }
        if (initialized) {
            renderSystem.initialize(renderBackend, this);
        }
        renderSystems.add(renderSystem);
    }

    @Override
    public void removeRenderSystem(RenderSystem renderSystem) {
        if (!renderSystems.remove(renderSystem)) {
            return;
        }
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
        long tickStart = System.nanoTime();
        scheduler.tick(deltaTimeSeconds);
        if (activeScene != null) {
            activeScene.advanceTick();
            captureTransformInterpolationSnapshots(activeScene);
            updateGameSystems(input, deltaTimeSeconds);
        }
        profiler.record(FrameProfiler.TICK_SECTION, System.nanoTime() - tickStart);
    }

    private void updateGameSystems(InputState input, float deltaTimeSeconds) {
        for (GameSystem system : gameSystems) {
            long systemStart = System.nanoTime();
            system.update(activeScene, input, deltaTimeSeconds);
            profiler.record(FrameProfiler.SYSTEM_PREFIX + system.getClass().getSimpleName(),
                    System.nanoTime() - systemStart);
        }
    }

    public FrameProfiler profiler() {
        return profiler;
    }

    private void captureTransformInterpolationSnapshots(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(Transform3D.class)
                    .ifPresent(Transform3D::captureInterpolationSnapshot);
        }
    }

    public Optional<GameObject> pickAt(Camera3D camera, int x, int y, int width, int height) {
        if (camera == null || width <= 0 || height <= 0) {
            return Optional.empty();
        }
        if (pickingPass == null) {
            pickingPass = new PickingPass(ShaderLoader.autoDetect());
        }
        if (activeScene == null) {
            return Optional.empty();
        }
        return pickingPass.pickAt(activeScene, camera, x, y, width, height, renderBackend);
    }

    public void render(List<Camera3D> activeCameras, RenderTargetHandle screenTarget, float interpolationAlpha) {
        long renderStart = System.nanoTime();
        frame.reset();
        collectRenderSystems(RenderContext.of(activeCameras, screenTarget, interpolationAlpha));
        renderBackend.beginFrame();
        long drainStart = System.nanoTime();
        drainStages(screenTarget);
        renderBackend.endFrame();
        profiler.record(FrameProfiler.DRAIN_SECTION, System.nanoTime() - drainStart);
        profiler.record(FrameProfiler.RENDER_SECTION, System.nanoTime() - renderStart);
        profiler.publishFrame();
        hud.clear();
    }

    private void collectRenderSystems(RenderContext context) {
        if (activeScene == null) {
            return;
        }
        long collectStart = System.nanoTime();
        for (RenderSystem system : renderSystems) {
            long systemStart = System.nanoTime();
            system.collect(activeScene, frame, context);
            profiler.record(FrameProfiler.COLLECT_PREFIX + system.getClass().getSimpleName(),
                    System.nanoTime() - systemStart);
        }
        profiler.record(FrameProfiler.COLLECT_SECTION, System.nanoTime() - collectStart);
    }

    public void onResize(int width, int height) {
        renderBackend.onViewportResize(width, height);
        for (RenderSystem system : renderSystems) {
            system.onResize(renderBackend, this, width, height);
        }
    }

    public void shutdown() {
        for (GameSystem system : gameSystems) {
            system.shutdown();
        }
        for (RenderSystem system : renderSystems) {
            system.shutdown(renderBackend);
        }
        if (pickingPass != null) {
            pickingPass.shutdown();
            pickingPass = null;
        }
        if (fontRegistry != null) {
            fontRegistry.destroyAll();
        }
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
    public void bindStagePreparation(RenderPass pass, Runnable preparation) {
        stagePreparations.computeIfAbsent(pass, ignored -> new ArrayList<>()).add(preparation);
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

    private PostProcessSettings resolvePostProcessSettings() {
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

    public long cpuTimingNanos(CpuTimings slot) {
        return cpuTimingsNanosArray[slot.ordinal()];
    }

    public void setCpuTimingNanos(CpuTimings slot, long nanos) {
        cpuTimingsNanosArray[slot.ordinal()] = nanos;
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
        StageBinding configured = stageBindings.get(pass);
        RenderPass followed = stageFollowers.get(pass);
        if (followed == null) {
            return configured;
        }
        return new StageBinding(stageBindings.get(followed).target(), configured.clear());
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
        bindings.put(RenderPasses.TRANSPARENT_3D, screenNoClear);
        bindings.put(RenderPasses.WORLD_2D, screenNoClear);
        bindings.put(RenderPasses.UI, screenNoClear);
        bindings.put(RenderPasses.POST, screenNoClear);
        return bindings;
    }

    private record StageBinding(RenderTargetHandle target, PassClear clear) {
    }
}
