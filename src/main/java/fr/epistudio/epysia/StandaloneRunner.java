package fr.epistudio.epysia;

import fr.epistudio.epysia.assets.loaders.ClipAssetLoader;
import fr.epistudio.epysia.assets.loaders.MaterialAssetLoader;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.assets.loaders.PhysicsMaterialLoader;
import fr.epistudio.epysia.assets.loaders.ProbesAssetLoader;
import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.MutableInputState;
import fr.epistudio.epysia.logging.CompositeLogger;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.LogFile;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.render.sprite.SpriteRenderSystem;
import fr.epistudio.epysia.render.text.TextRenderSystem;
import fr.epistudio.epysia.vfx.VfxRenderSystem;
import fr.epistudio.epysia.runtime.RuntimeCommand;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public final class StandaloneRunner {

    private static final long PROFILE_REPORT_INTERVAL_NANOS = 1_000_000_000L;
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    private static final double FIXED_TIMESTEP_SECONDS = 1.0 / 60.0;
    private static final double MAX_FRAME_SECONDS = 0.25;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    @FunctionalInterface
    public interface ScenePopulator {
        void populate(EpysiaEngine engine, EngineServices services) throws Exception;
    }

    private StandaloneRunner() {
    }

    public static void runStandalone(String windowTitle, int width, int height, ScenePopulator populator) {
        Window window = new Window(windowTitle, width, height);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        Logger logger = createLogger();
        installCrashHandler(logger);
        engine.setLogger(logger);
        Scene scene = new Scene("standalone");
        engine.addScene(scene);
        loadModulesInto(engine);
        ShaderLoader shaderLoader = ShaderLoader.autoDetect();
        ShaderWatcher shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
        MeshRenderSystem meshRenderSystem = new MeshRenderSystem(shaderLoader, shaderWatcher, engine.logger());
        engine.addRenderSystem(meshRenderSystem);
        engine.addRenderSystem(new VfxRenderSystem(shaderLoader, meshRenderSystem, engine.logger()));
        engine.addRenderSystem(new SpriteRenderSystem(shaderLoader, meshRenderSystem, engine.logger()));
        engine.addRenderSystem(new TextRenderSystem(shaderLoader, window, engine, engine.logger()));
        window.open();
        MutableInputState inputState = new MutableInputState();
        window.attachInput(inputState);
        backend.initialize(window);
        engine.initialize();
        BuiltinMeshes builtins = BuiltinMeshes.uploadAll(backend);
        engine.assets().register(new MeshAssetLoader(builtins));
        engine.assets().register(new TextureAssetLoader());
        engine.assets().register(new PhysicsMaterialLoader());
        engine.assets().register(new MaterialAssetLoader());
        engine.assets().register(new ClipAssetLoader());
        engine.assets().register(new ProbesAssetLoader());
        runPopulator(engine, populator);
        ensurePostProcessing(engine, shaderLoader, shaderWatcher, window);
        engine.beginPlay();
        loop(engine, window, inputState);
        engine.endPlay();
        engine.shutdown();
        backend.shutdown();
        window.close();
    }

    private static Logger createLogger() {
        PrintStream fileSink = LogFile.open(Path.of("logs", "epysia.log"));
        return new CompositeLogger(List.of(new ConsoleLogger(), new ConsoleLogger(fileSink)));
    }

    private static void installCrashHandler(Logger logger) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                logger.error("[uncaught] thread " + thread.getName(), error));
    }

    private static void ensurePostProcessing(EpysiaEngine engine, ShaderLoader shaderLoader,
                                             ShaderWatcher shaderWatcher, Window window) {
        if (engine.hasRenderSystem(PostProcessSystem.class)) {
            return;
        }
        PostProcessSystem postProcess = new PostProcessSystem(shaderLoader, window, engine.logger());
        postProcess.setShaderWatcher(shaderWatcher);
        engine.addRenderSystem(postProcess);
    }

    private static void loadModulesInto(EpysiaEngine engine) {
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

    private static void runPopulator(EpysiaEngine engine, ScenePopulator populator) {
        try {
            populator.populate(engine, engine);
            engine.scene().advanceTick();
            for (GameObject gameObject : engine.scene().drainRecentlyActivated()) {
                invokeOnLoad(engine, gameObject);
            }
        } catch (Exception error) {
            engine.logger().error("[StandaloneRunner] Scene populate failed", error);
            engine.requestShutdown();
        }
    }

    private static void invokeOnLoad(EpysiaEngine engine, GameObject gameObject) {
        for (IComponent component : new ArrayList<>(gameObject.components())) {
            try {
                component.onLoad(engine);
            } catch (RuntimeException error) {
                engine.logger().error("[StandaloneRunner] onLoad failed for "
                        + component.getClass().getName(), error);
            }
        }
    }

    public static void runLoop(EpysiaEngine engine, Window window, MutableInputState inputState) {
        loop(engine, window, inputState);
    }

    private static void loop(EpysiaEngine engine, Window window, MutableInputState inputState) {
        long previousNanos = System.nanoTime();
        long lastProfileReportNanos = previousNanos;
        int framesSinceReport = 0;
        double accumulator = 0.0;
        while (!window.shouldClose() && !engine.isShutdownRequested()) {
            long pollStart = System.nanoTime();
            window.pollEvents();
            handleResizeIfNeeded(window, engine);
            long pollEnd = System.nanoTime();
            consumeRuntimeCommands(engine);
            long currentNanos = System.nanoTime();
            double frameSeconds = Math.min((currentNanos - previousNanos) / NANOS_PER_SECOND, MAX_FRAME_SECONDS);
            previousNanos = currentNanos;
            long updateStart = System.nanoTime();
            accumulator = drainFixedSteps(engine, inputState, accumulator + frameSeconds);
            long updateEnd = System.nanoTime();
            float interpolationAlpha = (float) (accumulator / FIXED_TIMESTEP_SECONDS);
            List<Camera3D> activeCameras = collectActiveCameras(engine.scene());
            updateCameraAspect(activeCameras, window.framebufferWidth(), window.framebufferHeight());
            long renderStart = System.nanoTime();
            engine.render(activeCameras, RenderTargetHandle.SCREEN, interpolationAlpha);
            long renderEnd = System.nanoTime();
            long swapStart = System.nanoTime();
            window.swapBuffers();
            long swapEnd = System.nanoTime();
            engine.setCpuTimingNanos(CpuTimings.POLL, pollEnd - pollStart);
            engine.setCpuTimingNanos(CpuTimings.UPDATE, updateEnd - updateStart);
            engine.setCpuTimingNanos(CpuTimings.RENDER, renderEnd - renderStart);
            engine.setCpuTimingNanos(CpuTimings.SWAP_BUFFERS, swapEnd - swapStart);
            framesSinceReport++;
            if (swapEnd - lastProfileReportNanos >= PROFILE_REPORT_INTERVAL_NANOS) {
                reportProfile(engine, framesSinceReport, swapEnd - lastProfileReportNanos);
                lastProfileReportNanos = swapEnd;
                framesSinceReport = 0;
            }
        }
    }

    private static void reportProfile(EpysiaEngine engine, int frames, long elapsedNanos) {
        if (frames <= 0 || !Boolean.getBoolean("epysia.gpu.profiling")) {
            return;
        }
        double frameMillis = elapsedNanos / NANOS_PER_MILLI / frames;
        StringBuilder report = new StringBuilder();
        report.append(String.format("%.2f ms/frame (%.0f fps)", frameMillis, frames * NANOS_PER_SECOND / elapsedNanos));
        for (CpuTimings slot : CpuTimings.values()) {
            report.append(String.format(" | cpu.%s %.3f", slot.label(),
                    engine.cpuTimingNanos(slot) / NANOS_PER_MILLI));
        }
        for (Map.Entry<String, Long> entry : engine.profiler().sections().entrySet()) {
            report.append(String.format(" | %s %.3f", entry.getKey(), entry.getValue() / NANOS_PER_MILLI));
        }
        for (Map.Entry<String, Long> entry : engine.renderBackend().latestProfileTimingsNanos().entrySet()) {
            report.append(String.format(" | gpu.%s %.3f", entry.getKey(), entry.getValue() / NANOS_PER_MILLI));
        }
        engine.logger().info(report.toString());
    }

    private static double drainFixedSteps(EpysiaEngine engine, MutableInputState input, double accumulator) {
        float step = (float) FIXED_TIMESTEP_SECONDS;
        double remaining = accumulator;
        while (remaining >= FIXED_TIMESTEP_SECONDS) {
            engine.tick(input, step);
            input.advanceFrame();
            remaining -= FIXED_TIMESTEP_SECONDS;
        }
        return remaining;
    }

    private static void handleResizeIfNeeded(Window window, EpysiaEngine engine) {
        if (!window.consumeFramebufferResized()) {
            return;
        }
        int width = window.framebufferWidth();
        int height = window.framebufferHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        engine.onResize(width, height);
    }

    private static void consumeRuntimeCommands(EpysiaEngine engine) {
        Optional<RuntimeCommand> command = engine.runtimeChannel().pollCommand();
        while (command.isPresent()) {
            if (command.get() instanceof RuntimeCommand.Quit) {
                engine.requestShutdown();
                return;
            }
            command = engine.runtimeChannel().pollCommand();
        }
    }

    private static void updateCameraAspect(List<Camera3D> cameras, int width, int height) {
        if (height <= 0) {
            return;
        }
        float aspect = (float) width / (float) Math.max(1, height);
        for (Camera3D camera : cameras) {
            camera.setAspectRatio(aspect);
        }
    }

    private static List<Camera3D> collectActiveCameras(Scene scene) {
        List<Camera3D> cameras = new ArrayList<>();
        for (GameObject gameObject : scene.gameObjects()) {
            gameObject.getComponent(Camera3D.class)
                    .filter(Camera3D::active)
                    .ifPresent(cameras::add);
        }
        return cameras;
    }
}
