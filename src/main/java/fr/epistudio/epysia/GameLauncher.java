package fr.epistudio.epysia;

import fr.epistudio.epysia.assets.AssetDatabase;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.gpu.GpuLauncher;
import fr.epistudio.epysia.gpu.GpuPreference;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.diagnostics.CrashReporter;
import fr.epistudio.epysia.diagnostics.FrameRateOverlay;
import fr.epistudio.epysia.logging.LevelFilteredLogger;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.net.session.NetworkConfig;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.physics.api.CollisionLayers;
import fr.epistudio.epysia.project.EditorSettings;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.project.ProjectQuality;
import fr.epistudio.epysia.project.ProjectQualityProperties;
import fr.epistudio.epysia.project.NetworkSettings;
import fr.epistudio.epysia.render.GraphicsApi;
import fr.epistudio.epysia.settings.GameSettings;
import fr.epistudio.epysia.project.ProjectStore;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.runtime.ChannelLogger;
import fr.epistudio.epysia.runtime.NullRuntimeChannel;
import fr.epistudio.epysia.runtime.RuntimeChannel;
import fr.epistudio.epysia.runtime.RuntimeEvent;
import fr.epistudio.epysia.runtime.StdioRuntimeChannel;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.SceneLoadMode;
import fr.epistudio.epysia.scene.SceneLoader;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.scripting.ProjectRenderSetup;
import fr.epistudio.epysia.scripting.compile.ScriptLoadResult;
import fr.epistudio.epysia.scripting.compile.ScriptModule;
import fr.epistudio.epysia.window.Window;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class GameLauncher {
    /// One source of truth: the same defaults the project file falls back to when a key is absent.
    private static final ProjectQuality DEFAULT_QUALITY = ProjectQuality.defaults();

    private GameLauncher() {
    }

    private static GameSettings loadGameSettings() {
        Path directory = Path.of(System.getProperty("user.dir", "."));
        return GameSettings.load(GameSettings.fileIn(directory));
    }

    public static void main(String[] args) {
        GameSettings settings = loadGameSettings();
        GraphicsApi.select(settings.renderApi());
        GpuLauncher.enforce(GpuPreference.fromId(parseStringOr(args, "--gpu", settings.gpuAdapter())));
        Path scenePath = parseRequiredPath(args, "--scene");
        Optional<Path> projectRoot = parseOptionalPath(args, "--project");
        projectRoot.ifPresent(GameLauncher::applyProjectQuality);
        Optional<Path> precompiledScripts = parseOptionalPath(args, "--precompiled-scripts");
        String title = parseStringOr(args, "--title", DEFAULT_QUALITY.windowTitle());
        parseOptionalPath(args, "--icon").ifPresent(Window::setIconFile);
        int width = parseIntOr(args, "--width", settings.windowWidth());
        int height = parseIntOr(args, "--height", settings.windowHeight());
        System.setProperty("epysia.vsync",
                parseStringOr(args, "--vsync", String.valueOf(settings.verticalSync())));
        StandaloneRunner.setMaximumFrameRate(parseIntOr(args, "--max-fps",
                settings.frameRateCap()));
        boolean stdioChannel = hasFlag(args, "--runtime-channel=stdio");
        RuntimeChannel channel = stdioChannel ? new StdioRuntimeChannel() : new NullRuntimeChannel();
        Logger localFallback = new ConsoleLogger(System.err);
        Logger logger = LevelFilteredLogger.wrap(
                stdioChannel ? new ChannelLogger(channel, localFallback) : localFallback,
                settings.logLevel());
        boolean serverMode = hasFlag(args, "--server");
        float validateSeconds = parseFloatOr(args, "--validate", 0.0f);
        boolean rendering = !serverMode && validateSeconds <= 0.0f;
        boolean listenServerMode = hasFlag(args, "--listen-server");
        String connectHost = parseStringOr(args, "--connect", "");
        int port = parseIntOr(args, "--port", NetworkConfig.DEFAULT_PORT);
        String metricsHost = parseStringOr(args, "--metrics-host", "127.0.0.1");
        int metricsPort = parseIntOr(args, "--metrics-port", 0);
        CrashReporter.install(Path.of(System.getProperty("user.dir", ".")), logger);
        StandaloneRunner.ScenePopulator populator = (engine, services) -> {
            engine.setLogger(logger);
            engine.setRuntimeChannel(channel);
            if (settings.showFrameRate()) {
                services.systems().add(new FrameRateOverlay());
            }
            ComponentRegistry registry = new ComponentRegistry();
            registry.populateFromScan(ComponentScanner.scan());
            projectRoot.ifPresent(root -> {
                ScriptLoadResult scripts = loadScripts(root, precompiledScripts);
                for (String message : scripts.messages()) {
                    logger.info("[scripts] " + message);
                }
                registry.setUserComponents(scripts.components());
                if (rendering) {
                    runRenderSetups(scripts, services, logger);
                }
            });
            projectRoot.ifPresent(root -> attachAssetDatabase(services, root, logger));
            SceneSerializer serializer = new SceneSerializer(registry);
            engine.setSceneLoader(new SceneLoader(services, serializer));
            serializer.deserializeInto(services.scene(), Files.readString(scenePath), services,
                    SceneLoadMode.REPLACE, scenePath.toString());
            if (rendering) {
                ensureCameraExists(services.scene());
            }
            projectRoot.ifPresent(root -> applyProjectSettings(engine, services, root, logger, !rendering));
            logger.info("Loaded scene " + scenePath
                    + (projectRoot.isPresent() ? " (project " + projectRoot.get() + ")" : ""));
            startNetworking(services, logger, new NetworkStartup(serverMode, listenServerMode,
                    connectHost, port, metricsHost, metricsPort), readNetworkSettings(projectRoot));
        };
        if (validateSeconds > 0.0f) {
            logger.info("[validate] headless smoke run for " + validateSeconds + " s");
            HeadlessRunner.setRunDurationSeconds(validateSeconds);
            boolean survived = HeadlessRunner.run(populator);
            logger.info("[validate] " + (survived ? "completed without crashing" : "FAILED"));
            channel.close();
            System.exit(survived ? 0 : 1);
        } else if (serverMode) {
            logger.info("[server] starting a dedicated server on port " + port);
            HeadlessRunner.run(populator);
        } else {
            channel.send(new RuntimeEvent.Ready(title, width, height));
            StandaloneRunner.runStandalone(title, width, height, populator);
        }
        channel.send(new RuntimeEvent.Stopped("normal"));
        channel.close();
    }

    private record NetworkStartup(boolean dedicatedServer, boolean listenServer,
                                  String connectHost, int port, String metricsHost, int metricsPort) {
        private boolean requested() {
            return dedicatedServer || listenServer || !connectHost.isEmpty();
        }
    }

    private static void startDiagnosticsIfRequested(EngineServices services, NetworkStartup startup) {
        if (startup.metricsPort() <= 0) {
            return;
        }
        services.network().startDiagnostics(startup.metricsHost(), startup.metricsPort());
    }

    private static void startNetworking(EngineServices services, Logger logger, NetworkStartup startup,
                                        NetworkSettings settings) {
        if (!startup.requested()) {
            return;
        }
        NetworkConfig config = networkConfigFrom(settings, startup);
        if (!settings.joinSecretConfigured()) {
            logger.warn("[net] no join secret configured: any client may connect."
                    + " Set one under Settings, Network.");
        }
        if (startup.dedicatedServer()) {
            services.network().startServer(config);
            logger.info("[server] listening on port " + startup.port());
            startDiagnosticsIfRequested(services, startup);
            return;
        }
        if (startup.listenServer()) {
            services.network().startListenServer(config);
            logger.info("[net] hosting a listen server on port " + startup.port());
            return;
        }
        services.network().connect(config, startup.connectHost(), startup.port());
        logger.info("[net] connecting to " + startup.connectHost() + ":" + startup.port());
    }

    private static void runRenderSetups(ScriptLoadResult scripts, EngineServices services, Logger logger) {
        for (Class<? extends ProjectRenderSetup> setupClass : scripts.renderSetups()) {
            try {
                setupClass.getDeclaredConstructor().newInstance().configure(services);
                logger.info("[scripts] applied render setup " + setupClass.getName());
            } catch (ReflectiveOperationException | RuntimeException error) {
                logger.error("[scripts] render setup failed: " + setupClass.getName(), error);
            }
        }
    }

    private static ScriptLoadResult loadScripts(Path projectRoot, Optional<Path> precompiledScripts) {
        ProjectLibraries libraries = ProjectLibraries.forProjectRoot(projectRoot);
        if (precompiledScripts.isPresent()) {
            return ScriptModule.loadPrecompiled(precompiledScripts.get(), libraries);
        }
        return ScriptModule.load(projectRoot.resolve(Project.SCRIPTS_DIRECTORY_NAME),
                projectRoot.resolve(".epysia/scripts-out"), libraries);
    }

    private static void attachAssetDatabase(EngineServices services, Path projectRoot, Logger logger) {
        try {
            services.assets().attachProject(projectRoot);
            logger.info("[assets] opened asset database for " + projectRoot);
        } catch (UncheckedIOException error) {
            logger.error("[assets] failed to open asset database for " + projectRoot, error);
        }
    }

    private static void applyProjectSettings(EpysiaEngine engine, EngineServices services,
                                             Path projectRoot, Logger logger, boolean serverMode) {
        ProjectStore store = new ProjectStore();
        store.readProjectFromDisk(projectRoot, 0L).ifPresent(project -> {
            EditorSettings settings = store.readSettings(project);
            ProjectQuality quality = store.readQuality(project);
            engine.gameSystem(PhysicsSystem.class).ifPresent(physics -> {
                physics.setCollisionLayers(CollisionLayers.from(settings.collisionMatrix()));
                physics.setGravity(quality.gravityX(), quality.gravityY(), quality.gravityZ());
                physics.setFixedTimestepHertz(quality.fixedTimestepHertz());
                logger.info("[physics] applied collision layers, gravity and fixed timestep");
            });
            if (!serverMode) {
                MeshRenderSystem meshes = engine.renderSystem(MeshRenderSystem.class);
                meshes.applyTuning(quality.renderTuning());
                meshes.setDepthPrepassEnabled(quality.depthPrepass());
            }
            services.inputActions().replaceAll(store.readInputActions(project));
        });
    }

    private static NetworkSettings readNetworkSettings(Optional<Path> projectRoot) {
        if (projectRoot.isEmpty()) {
            return NetworkSettings.defaults();
        }
        ProjectStore store = new ProjectStore();
        return store.readProjectFromDisk(projectRoot.get(), 0L)
                .map(store::readNetwork)
                .orElseGet(NetworkSettings::defaults);
    }

    private static NetworkConfig networkConfigFrom(NetworkSettings settings, NetworkStartup startup) {
        NetworkConfig config = new NetworkConfig()
                .setPort(startup.port())
                .setMaximumPeers(settings.maximumPeers())
                .setNetworkTickRate(settings.networkTickRate())
                .setSnapshotRate(settings.snapshotRate())
                .setInterpolationDelayTicks(settings.interpolationDelayTicks())
                .setTimeoutSeconds(settings.timeoutSeconds());
        return config.setJoinSecret(settings.joinSecret());
    }

    private static void applyProjectQuality(Path projectRoot) {
        ProjectStore store = new ProjectStore();
        store.readProjectFromDisk(projectRoot, 0L).ifPresent(project -> {
            ProjectQuality quality = store.readQuality(project);
            StandaloneRunner.setFixedTimestepSeconds(quality.fixedTimestepSeconds());
            HeadlessRunner.setFixedTimestepSeconds(quality.fixedTimestepSeconds());
            ProjectQualityProperties.apply(quality);
        });
    }

    private static void ensureCameraExists(Scene scene) {
        for (GameObject gameObject : scene.gameObjects()) {
            if (gameObject.getComponent(Camera3D.class).isPresent()) {
                return;
            }
        }
        GameObject fallback = new GameObject("Fallback Camera");
        Transform3D transform = new Transform3D().setPosition(6.0f, 5.0f, 8.0f);
        transform.lookAt(0.0f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f);
        fallback.addComponent(transform);
        fallback.addComponent(new Camera3D().setFieldOfViewDegrees(60.0f).setNearFar(0.05f, 500.0f));
        scene.addGameObject(fallback);
        scene.advanceTick();
    }

    private static Path parseRequiredPath(String[] args, String flag) {
        String value = findFlagValue(args, flag);
        if (value == null) {
            System.err.println("GameLauncher: missing required flag " + flag);
            System.exit(2);
        }
        return Path.of(value);
    }

    private static Optional<Path> parseOptionalPath(String[] args, String flag) {
        String value = findFlagValue(args, flag);
        return value == null ? Optional.empty() : Optional.of(Path.of(value));
    }

    private static String parseStringOr(String[] args, String flag, String fallback) {
        String value = findFlagValue(args, flag);
        return value == null ? fallback : value;
    }

    private static float parseFloatOr(String[] args, String flag, float fallback) {
        String value = findFlagValue(args, flag);
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static int parseIntOr(String[] args, String flag, int fallback) {
        String value = findFlagValue(args, flag);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String findFlagValue(String[] args, String flag) {
        for (int index = 0; index < args.length - 1; index++) {
            if (flag.equals(args[index])) {
                return args[index + 1];
            }
        }
        return null;
    }
}
