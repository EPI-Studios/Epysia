package fr.epistudio.epysia.editor.play;

import fr.epistudio.epysia.EngineModule;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.SystemRegistryImpl;
import fr.epistudio.epysia.editor.EditorComponentRegistry;
import fr.epistudio.epysia.editor.serialization.SceneSerializer;
import fr.epistudio.epysia.logging.ConsoleLogger;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.mesh.MeshRenderSystem;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

public final class PlayRunner {

    private static final String DEFAULT_TITLE = "Epysia — Play";
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private PlayRunner() {
    }

    public static void main(String[] args) {
        Path scenePath = parseRequiredPath(args, "--scene");
        String title = parseStringOr(args, "--title", DEFAULT_TITLE);
        int width = parseIntOr(args, "--width", DEFAULT_WIDTH);
        int height = parseIntOr(args, "--height", DEFAULT_HEIGHT);

        Window window = new Window(title, width, height);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);

        EditorComponentRegistry componentRegistry = new EditorComponentRegistry();
        componentRegistry.populateFromScan(ComponentScanner.scan());
        SceneSerializer serializer = new SceneSerializer(componentRegistry);
        Scene scene = new Scene("playmode");

        SystemRegistryImpl systemRegistry = new SystemRegistryImpl();
        List<EngineModule> modules = new ArrayList<>();
        for (EngineModule module : ServiceLoader.load(EngineModule.class)) {
            modules.add(module);
        }
        modules.sort(Comparator.comparingInt(EngineModule::order));
        for (EngineModule module : modules) {
            module.registerSystems(systemRegistry);
        }
        for (fr.epistudio.epysia.GameSystem system : systemRegistry.systems()) {
            engine.addSystem(system);
        }

        ShaderLoader shaderLoader = ShaderLoader.autoDetect();
        ShaderWatcher shaderWatcher = new ShaderWatcher(shaderLoader.filesystemRoot());
        engine.addRenderSystem(new MeshRenderSystem(shaderLoader, shaderWatcher,
                new ConsoleLogger(), window));

        engine.onStartup(() -> {
            try {
                serializer.load(scene, scenePath);
                System.out.println("[PlayRunner] Loaded scene with "
                        + scene.gameObjects().size() + " object(s) from " + scenePath);
            } catch (Exception error) {
                System.err.println("[PlayRunner] Failed to load scene: " + error);
                engine.requestShutdown();
            }
        });
        engine.addScene(scene);
        engine.run();
    }

    private static Path parseRequiredPath(String[] args, String flag) {
        String value = findFlagValue(args, flag);
        if (value == null) {
            System.err.println("PlayRunner: missing required flag " + flag);
            System.exit(2);
        }
        return Path.of(value);
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

    private static String findFlagValue(String[] args, String flag) {
        for (int index = 0; index < args.length - 1; index++) {
            if (flag.equals(args[index])) {
                return args[index + 1];
            }
        }
        return null;
    }
}
