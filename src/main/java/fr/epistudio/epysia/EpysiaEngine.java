package fr.epistudio.epysia;

import fr.epistudio.epysia.input.KeyCode;
import fr.epistudio.epysia.input.MutableInputState;
import fr.epistudio.epysia.render.Frame;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.Stage;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.DrawCommand;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.text.Font;
import fr.epistudio.epysia.render.text.FontRegistry;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class EpysiaEngine implements StageConfigurer, EngineServices {

    private static final double FIXED_TIMESTEP_SECONDS = 1.0 / 60.0;
    private static final double MAX_FRAME_SECONDS = 0.25;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final PassClear DEFAULT_CLEAR = PassClear.color(0.10f, 0.12f, 0.18f);

    private final Window window;
    private final RenderBackend renderBackend;
    private final List<Scene> scenes = new ArrayList<>();
    private final List<GameSystem> gameSystems = new ArrayList<>();
    private final SystemRegistryImpl systemRegistry = new SystemRegistryImpl();
    private final List<RenderSystem> renderSystems = new ArrayList<>();
    private final Frame frame = new Frame();
    private final Map<Stage, StageBinding> stageBindings = defaultStageBindings();
    private final MutableInputState inputState = new MutableInputState();
    private final long[] cpuTimingsNanosArray = new long[CpuTimings.values().length];
    private FontRegistry fontRegistry;
    private Runnable startupHook = () -> {};
    private volatile boolean running;
    private double targetFrameSeconds;

    public EpysiaEngine(Window window, RenderBackend renderBackend) {
        this.window = window;
        this.renderBackend = renderBackend;
    }

    public void addScene(Scene scene) {
        scenes.add(scene);
    }

    public void addSystem(GameSystem system) {
        gameSystems.add(system);
        systemRegistry.add(system);
    }

    public void addRenderSystem(RenderSystem renderSystem) {
        renderSystems.add(renderSystem);
    }

    public void onStartup(Runnable hook) {
        this.startupHook = hook;
    }

    public void requestShutdown() {
        running = false;
    }

    @Override
    public void bindStageTarget(Stage stage, RenderTargetHandle target, PassClear clear) {
        stageBindings.put(stage, new StageBinding(target, clear));
    }

    public void run() {
        window.open();
        window.attachInput(inputState);
        renderBackend.initialize(window);
        fontRegistry = new FontRegistry(renderBackend);
        fontRegistry.load(FontRegistry.DEFAULT_NAME, "fonts/AdwaitaMono-Regular.ttf", 24.0f);
        initializeRenderSystems();
        initializeGameSystems();
        startupHook.run();
        loop();
        shutdownGameSystems();
        shutdownRenderSystems();
        fontRegistry.destroyAll();
        renderBackend.shutdown();
        window.close();
    }

    @Override
    public FontRegistry fonts() {
        return fontRegistry;
    }

    private void initializeGameSystems() {
        for (GameSystem system : gameSystems) {
            system.initialize(this);
        }
    }

    private void shutdownGameSystems() {
        for (GameSystem system : gameSystems) {
            system.shutdown();
        }
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
    public Scene scene() {
        if (scenes.isEmpty()) {
            throw new fr.epistudio.epysia.exceptions.EpysiaException(
                    "EngineServices.scene() requested but no scene registered with the engine.");
        }
        return scenes.get(0);
    }

    @Override
    public SystemRegistry systems() {
        return systemRegistry;
    }

    public long cpuTimingNanos(CpuTimings slot) {
        return cpuTimingsNanosArray[slot.ordinal()];
    }

    public void setTargetFrameRate(int framesPerSecond) {
        targetFrameSeconds = framesPerSecond > 0 ? 1.0 / framesPerSecond : 0.0;
    }

    private void initializeRenderSystems() {
        for (RenderSystem system : renderSystems) {
            system.initialize(renderBackend, this);
        }
    }

    private void shutdownRenderSystems() {
        for (RenderSystem system : renderSystems) {
            system.shutdown(renderBackend);
        }
    }

    private void handleResizeIfNeeded() {
        if (!window.consumeFramebufferResized()) {
            return;
        }
        int width = window.framebufferWidth();
        int height = window.framebufferHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        renderBackend.onViewportResize(width, height);
        for (RenderSystem system : renderSystems) {
            system.onResize(renderBackend, this, width, height);
        }
    }

    private void loop() {
        running = true;
        long previousNanos = System.nanoTime();
        double accumulator = 0.0;
        while (running && !window.shouldClose()) {
            long pollStart = System.nanoTime();
            window.pollEvents();
            handleResizeIfNeeded();
            long pollEnd = System.nanoTime();
            if (inputState.isKeyDown(KeyCode.ESCAPE)) {
                running = false;
                break;
            }
            long currentNanos = System.nanoTime();
            double frameSeconds = Math.min((currentNanos - previousNanos) / NANOS_PER_SECOND, MAX_FRAME_SECONDS);
            previousNanos = currentNanos;
            long updateStart = System.nanoTime();
            accumulator = drainFixedSteps(accumulator + frameSeconds);
            long updateEnd = System.nanoTime();
            renderFrame((float) (accumulator / FIXED_TIMESTEP_SECONDS));
            long renderEnd = System.nanoTime();
            inputState.consumeFrameDeltas();
            cpuTimingsNanosArray[CpuTimings.POLL.ordinal()] = pollEnd - pollStart;
            cpuTimingsNanosArray[CpuTimings.UPDATE.ordinal()] = updateEnd - updateStart;
            cpuTimingsNanosArray[CpuTimings.RENDER.ordinal()] = renderEnd - updateEnd;
            throttleFrame(pollStart);
        }
    }

    private static final long SPIN_TAIL_NANOS = 2_000_000L;

    private void throttleFrame(long frameStartNanos) {
        if (targetFrameSeconds <= 0.0) {
            return;
        }
        long targetEndNanos = frameStartNanos + (long) (targetFrameSeconds * NANOS_PER_SECOND);
        sleepUntil(targetEndNanos - SPIN_TAIL_NANOS);
        spinUntil(targetEndNanos);
    }

    private void sleepUntil(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return;
        }
        try {
            Thread.sleep(remainingNanos / 1_000_000L, (int) (remainingNanos % 1_000_000L));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void spinUntil(long deadlineNanos) {
        while (System.nanoTime() < deadlineNanos) {
            Thread.onSpinWait();
        }
    }

    private double drainFixedSteps(double accumulator) {
        float step = (float) FIXED_TIMESTEP_SECONDS;
        double remaining = accumulator;
        while (remaining >= FIXED_TIMESTEP_SECONDS) {
            tickScenes(step);
            remaining -= FIXED_TIMESTEP_SECONDS;
        }
        return remaining;
    }

    private void tickScenes(float deltaTimeSeconds) {
        for (Scene scene : scenes) {
            scene.advanceTick();
            for (GameSystem system : gameSystems) {
                system.update(scene, inputState, deltaTimeSeconds);
            }
        }
    }

    private void renderFrame(float interpolationAlpha) {
        long collectStart = System.nanoTime();
        collectFrame(interpolationAlpha);
        long collectEnd = System.nanoTime();
        renderBackend.beginFrame();
        long drainStart = System.nanoTime();
        drainStages();
        long drainEnd = System.nanoTime();
        renderBackend.endFrame();
        long swapStart = System.nanoTime();
        window.swapBuffers();
        long swapEnd = System.nanoTime();
        cpuTimingsNanosArray[CpuTimings.COLLECT.ordinal()] = collectEnd - collectStart;
        cpuTimingsNanosArray[CpuTimings.DRAIN_SUBMIT.ordinal()] = drainEnd - drainStart;
        cpuTimingsNanosArray[CpuTimings.SWAP_BUFFERS.ordinal()] = swapEnd - swapStart;
    }

    private void collectFrame(float interpolationAlpha) {
        frame.reset();
        for (Scene scene : scenes) {
            for (RenderSystem system : renderSystems) {
                system.collect(scene, frame, interpolationAlpha);
            }
        }
    }

    private void drainStages() {
        boolean screenWasOpened = false;
        for (Stage stage : Stage.values()) {
            List<DrawCommand> commands = frame.commandsFor(stage);
            if (commands.isEmpty()) {
                continue;
            }
            StageBinding binding = stageBindings.get(stage);
            renderBackend.beginProfileSection(stage.name());
            renderBackend.beginPass(binding.target(), binding.clear());
            for (DrawCommand command : commands) {
                renderBackend.execute(command);
            }
            renderBackend.endPass();
            renderBackend.endProfileSection();
            if (binding.target().id() == 0L) {
                screenWasOpened = true;
            }
        }
        if (!screenWasOpened) {
            renderBackend.beginPass(RenderTargetHandle.SCREEN, DEFAULT_CLEAR);
            renderBackend.endPass();
        }
    }

    private static Map<Stage, StageBinding> defaultStageBindings() {
        Map<Stage, StageBinding> bindings = new EnumMap<>(Stage.class);
        StageBinding screenWithClear = new StageBinding(RenderTargetHandle.SCREEN, DEFAULT_CLEAR);
        StageBinding screenNoClear = new StageBinding(RenderTargetHandle.SCREEN, PassClear.none());
        bindings.put(Stage.PRE_3D, screenWithClear);
        bindings.put(Stage.OPAQUE_3D, screenWithClear);
        bindings.put(Stage.TRANSPARENT_3D, screenNoClear);
        bindings.put(Stage.WORLD_2D, screenNoClear);
        bindings.put(Stage.UI, screenNoClear);
        bindings.put(Stage.POST, screenNoClear);
        return bindings;
    }

    private record StageBinding(RenderTargetHandle target, PassClear clear) {
    }
}
