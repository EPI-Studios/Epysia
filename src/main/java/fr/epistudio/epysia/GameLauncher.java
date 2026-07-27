package fr.epistudio.epysia;

import fr.epistudio.epysia.assets.AssetDatabase;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.gpu.GpuLauncher;
import fr.epistudio.epysia.gpu.GpuPreference;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.logging.Logger;
import fr.epistudio.epysia.physics.PhysicsSystem;
import fr.epistudio.epysia.physics.api.CollisionLayers;
import fr.epistudio.epysia.project.EditorSettings;
import fr.epistudio.epysia.project.ProjectQuality;
import fr.epistudio.epysia.project.ProjectQualityProperties;
import fr.epistudio.epysia.project.ProjectStore;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.runtime.ChannelLogger;
import fr.epistudio.epysia.runtime.NullRuntimeChannel;
import fr.epistudio.epysia.runtime.RuntimeChannel;
import fr.epistudio.epysia.runtime.RuntimeEvent;
import fr.epistudio.epysia.runtime.StdioRuntimeChannel;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.scripting.ProjectRenderSetup;
import fr.epistudio.epysia.scripting.compile.ScriptLoadResult;
import fr.epistudio.epysia.scripting.compile.ScriptModule;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;

public final class GameLauncher {

    private static final String DEFAULT_TITLE = "Epysia - Game";
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private GameLauncher() {
    }

    public static void main(String[] args) {
        GpuLauncher.enforce(GpuPreference.fromId(parseStringOr(args, "--gpu", "system")));
        Path scenePath = parseRequiredPath(args, "--scene");
        Optional<Path> projectRoot = parseOptionalPath(args, "--project");
        projectRoot.ifPresent(GameLauncher::applyProjectQuality);
        Optional<Path> precompiledScripts = parseOptionalPath(args, "--precompiled-scripts");
        String title = parseStringOr(args, "--title", DEFAULT_TITLE);
        int width = parseIntOr(args, "--width", DEFAULT_WIDTH);
        int height = parseIntOr(args, "--height", DEFAULT_HEIGHT);
        System.setProperty("epysia.vsync", parseStringOr(args, "--vsync", "true"));
        StandaloneRunner.setMaximumFrameRate(parseIntOr(args, "--max-fps", 0));
        boolean stdioChannel = hasFlag(args, "--runtime-channel=stdio");
        RuntimeChannel channel = stdioChannel ? new StdioRuntimeChannel() : new NullRuntimeChannel();
        Logger localFallback = new ConsoleLogger(System.err);
        Logger logger = stdioChannel ? new ChannelLogger(channel, localFallback) : localFallback;
        channel.send(new RuntimeEvent.Ready(title, width, height));
        StandaloneRunner.runStandalone(title, width, height, (engine, services) -> {
            engine.setLogger(logger);
            engine.setRuntimeChannel(channel);
            ComponentRegistry registry = new ComponentRegistry();
            registry.populateFromScan(ComponentScanner.scan());
            projectRoot.ifPresent(root -> {
                ScriptLoadResult scripts = loadScripts(root, precompiledScripts);
                for (String message : scripts.messages()) {
                    logger.info("[scripts] " + message);
                }
                registry.setUserComponents(scripts.components());
                runRenderSetups(scripts, services, logger);
            });
            projectRoot.ifPresent(root -> attachAssetDatabase(services, root, logger));
            SceneSerializer serializer = new SceneSerializer(registry);
            serializer.load(services.scene(), scenePath, services);
            ensureCameraExists(services.scene());
            projectRoot.ifPresent(root -> applyCollisionLayers(services, root, logger));
            logger.info("Loaded scene " + scenePath
                    + (projectRoot.isPresent() ? " (project " + projectRoot.get() + ")" : ""));
        });
        channel.send(new RuntimeEvent.Stopped("normal"));
        channel.close();
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
        if (precompiledScripts.isPresent()) {
            return ScriptModule.loadPrecompiled(precompiledScripts.get());
        }
        return ScriptModule.load(projectRoot.resolve("scripts"), projectRoot.resolve(".epysia/scripts-out"));
    }

    private static void attachAssetDatabase(EngineServices services, Path projectRoot, Logger logger) {
        try {
            services.assets().setDatabase(AssetDatabase.open(projectRoot));
            logger.info("[assets] opened asset database for " + projectRoot);
        } catch (UncheckedIOException error) {
            logger.error("[assets] failed to open asset database for " + projectRoot, error);
        }
    }

    private static void applyCollisionLayers(EngineServices services, Path projectRoot, Logger logger) {
        ProjectStore store = new ProjectStore();
        store.readProjectFromDisk(projectRoot, 0L).ifPresent(project -> {
            EditorSettings settings = store.readSettings(project);
            ProjectQuality quality = store.readQuality(project);
            PhysicsSystem physics = services.systems().get(PhysicsSystem.class);
            if (physics != null) {
                physics.setCollisionLayers(CollisionLayers.from(settings.collisionMatrix()));
                physics.setGravity(quality.gravityX(), quality.gravityY(), quality.gravityZ());
                logger.info("[physics] applied collision layer matrix and gravity");
            }
            services.inputActions().replaceAll(store.readInputActions(project));
        });
    }

    private static void applyProjectQuality(Path projectRoot) {
        ProjectStore store = new ProjectStore();
        store.readProjectFromDisk(projectRoot, 0L).ifPresent(project -> {
            ProjectQuality quality = store.readQuality(project);
            StandaloneRunner.setFixedTimestepSeconds(quality.fixedTimestepSeconds());
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
