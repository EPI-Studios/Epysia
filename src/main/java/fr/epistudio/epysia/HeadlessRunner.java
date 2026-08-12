package fr.epistudio.epysia;

import fr.epistudio.epysia.assets.loaders.ClipAssetLoader;
import fr.epistudio.epysia.assets.loaders.InstancesAssetLoader;
import fr.epistudio.epysia.assets.loaders.MaterialAssetLoader;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.assets.loaders.AudioBufferLoaderAsset;
import fr.epistudio.epysia.assets.loaders.PhysicsMaterialLoader;
import fr.epistudio.epysia.assets.loaders.ProbesAssetLoader;
import fr.epistudio.epysia.assets.loaders.SpriteAtlasAssetLoader;
import fr.epistudio.epysia.assets.loaders.SpriteTilemapAssetLoader;
import fr.epistudio.epysia.assets.loaders.TextureAssetLoader;
import fr.epistudio.epysia.components.IComponent;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.InputState;
import fr.epistudio.epysia.logging.CompositeLogger;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.LogFile;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.render.backend.NullRenderBackend;
import fr.epistudio.epysia.render.mesh.BuiltinMeshes;
import fr.epistudio.epysia.runtime.RuntimeCommand;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

public final class HeadlessRunner {
    private static final String WINDOW_TITLE = "Epysia - Server";
    private static final int DECLARED_WIDTH = 1;
    private static final int DECLARED_HEIGHT = 1;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double MAX_FRAME_SECONDS = 0.25;

    private static double fixedTimestepSeconds = 1.0 / 60.0;

    private HeadlessRunner() {
    }

    private static double runDurationSeconds;

    public static void setRunDurationSeconds(double seconds) {
        runDurationSeconds = Math.max(0.0, seconds);
    }

    public static void setFixedTimestepSeconds(double seconds) {
        fixedTimestepSeconds = seconds;
    }

    private static boolean populateFailed;

    public static boolean run(StandaloneRunner.ScenePopulator populator) {
        populateFailed = false;
        Window window = Window.headless(WINDOW_TITLE, DECLARED_WIDTH, DECLARED_HEIGHT);
        NullRenderBackend backend = new NullRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        Logger logger = createLogger();
        installCrashHandler(logger);
        engine.setLogger(logger);
        engine.addScene(new Scene("server"));
        loadHeadlessModulesInto(engine);
        installStopHandler(engine, logger);
        backend.initialize(window);
        engine.initialize();
        registerAssetLoaders(engine, backend);
        runPopulator(engine, populator);
        engine.beginPlay();
        loop(engine, window);
        engine.endPlay();
        engine.shutdown();
        backend.shutdown();
        return !populateFailed;
    }

    private static void registerAssetLoaders(EpysiaEngine engine, NullRenderBackend backend) {
        engine.assets().register(new MeshAssetLoader(BuiltinMeshes.uploadAll(backend)));
        engine.assets().register(new TextureAssetLoader());
        engine.assets().register(new PhysicsMaterialLoader());
        engine.assets().register(new AudioBufferLoaderAsset());
        engine.assets().register(new MaterialAssetLoader());
        engine.assets().register(new ClipAssetLoader());
        engine.assets().register(new ProbesAssetLoader());
        engine.assets().register(new InstancesAssetLoader());
        engine.assets().register(new SpriteAtlasAssetLoader());
        engine.assets().register(new SpriteTilemapAssetLoader());
    }

    private static Logger createLogger() {
        PrintStream fileSink = LogFile.open(Path.of("logs", "epysia-server.log"));
        return new CompositeLogger(List.of(new ConsoleLogger(), new ConsoleLogger(fileSink)));
    }

    private static void installCrashHandler(Logger logger) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                logger.error("[uncaught] thread " + thread.getName(), error));
    }

    private static void installStopHandler(EpysiaEngine engine, Logger logger) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("[server] stop signal received");
            engine.requestShutdown();
        }, "epysia-server-stop"));
    }

    private static void loadHeadlessModulesInto(EpysiaEngine engine) {
        SystemRegistryImpl registry = new SystemRegistryImpl();
        List<EngineModule> modules = new ArrayList<>();
        for (EngineModule module : ServiceLoader.load(EngineModule.class)) {
            if (module.runsHeadless()) {
                modules.add(module);
            }
        }
        modules.sort(Comparator.comparingInt(EngineModule::order));
        for (EngineModule module : modules) {
            module.registerSystems(registry);
        }
        for (GameSystem system : registry.systems()) {
            engine.addSystem(system);
        }
    }

    private static void runPopulator(EpysiaEngine engine, StandaloneRunner.ScenePopulator populator) {
        try {
            populator.populate(engine, engine);
            engine.scene().advanceTick();
            for (GameObject gameObject : engine.scene().drainRecentlyActivated()) {
                invokeOnLoad(engine, gameObject);
            }
        } catch (Exception error) {
            populateFailed = true;
            engine.logger().error("[HeadlessRunner] Scene populate failed", error);
            engine.requestShutdown();
        }
    }

    private static void invokeOnLoad(EpysiaEngine engine, GameObject gameObject) {
        for (IComponent component : new ArrayList<>(gameObject.components())) {
            try {
                component.onLoad(engine);
            } catch (RuntimeException error) {
                engine.logger().error("[HeadlessRunner] onLoad failed for "
                        + component.getClass().getName(), error);
            }
        }
    }

    private static long deadlineFrom(long startNanos) {
        return runDurationSeconds <= 0.0
                ? Long.MAX_VALUE
                : startNanos + (long) (runDurationSeconds * NANOS_PER_SECOND);
    }

    private static void loop(EpysiaEngine engine, Window window) {
        long previousNanos = System.nanoTime();
        long deadlineNanos = deadlineFrom(previousNanos);
        double accumulator = 0.0;
        while (!window.shouldClose() && !engine.isShutdownRequested()
                && System.nanoTime() < deadlineNanos) {
            consumeRuntimeCommands(engine);
            long currentNanos = System.nanoTime();
            double frameSeconds = Math.min((currentNanos - previousNanos) / NANOS_PER_SECOND, MAX_FRAME_SECONDS);
            previousNanos = currentNanos;
            accumulator = drainFixedSteps(engine, accumulator + frameSeconds);
            sleepUntilNextTick(accumulator);
        }
    }

    private static double drainFixedSteps(EpysiaEngine engine, double accumulator) {
        float step = (float) fixedTimestepSeconds;
        double remaining = Math.max(-fixedTimestepSeconds,
                accumulator + engine.consumeCatchUpSteps() * fixedTimestepSeconds);
        while (remaining >= fixedTimestepSeconds) {
            engine.tick(InputState.inactive(), step);
            remaining -= fixedTimestepSeconds;
        }
        return remaining;
    }

    private static void sleepUntilNextTick(double accumulator) {
        double remainingSeconds = fixedTimestepSeconds - accumulator;
        if (remainingSeconds <= 0.0) {
            return;
        }
        try {
            Thread.sleep((long) (remainingSeconds * 1_000.0), 0);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
}
