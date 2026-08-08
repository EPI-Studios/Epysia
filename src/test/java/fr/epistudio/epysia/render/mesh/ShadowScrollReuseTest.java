package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.assets.loaders.MeshAssetLoader;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.input.MutableInputState;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.opengl.OpenGlRenderBackend;
import fr.epistudio.epysia.render.shader.ShaderLoader;
import fr.epistudio.epysia.render.shader.ShaderWatcher;
import fr.epistudio.epysia.reflection.ComponentRegistry;
import fr.epistudio.epysia.reflection.ComponentScanner;
import fr.epistudio.epysia.scene.serialization.SceneSerializer;
import fr.epistudio.epysia.scripting.compile.ScriptLoadResult;
import fr.epistudio.epysia.scripting.compile.ScriptModule;
import fr.epistudio.epysia.project.Project;
import fr.epistudio.epysia.project.ProjectLibraries;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowScrollReuseTest {
    private static final int MOVING_FRAMES = 40;
    private static final int BENCHMARK_BLOCKS = Integer.getInteger("epysia.shadow.benchmarkBlocks", 600);
    private static final int BENCHMARK_WARMUP_FRAMES = 90;
    private static final int BENCHMARK_FRAMES = 400;
    private static final int SETTLING_FRAMES = 12;
    private static final float CAMERA_STEP = 0.07f;
    private static final float DEPTH_TOLERANCE = 1.0e-4f;
    private static final double MISMATCH_LIMIT = 0.005;

    private final MutableInputState input = new MutableInputState();

    private int observedScrolls;

    @Test
    void scrolledCascadeMatchesFullRebuild() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the shadow scroll check");
        if (System.getProperty("epysia.shadow.scroll") == null) {
            System.setProperty("epysia.shadow.scroll", "true");
        }
        Assumptions.assumeTrue(Boolean.getBoolean("epysia.shadow.scroll"),
                "static layer reuse is off, nothing to check");
        System.setProperty("epysia.offscreen", "true");
        Window window = new Window("shadow scroll check", 640, 480);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            runCheck(window, backend, engine);
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    @Test
    void reportsShadowCostWhileMoving() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the shadow cost report");
        Assumptions.assumeTrue(Boolean.getBoolean("epysia.shadow.benchmark"),
                "-Depysia.shadow.benchmark=true to run the shadow cost report");
        System.setProperty("epysia.offscreen", "true");
        Window window = new Window("shadow cost report", 1280, 720);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            runCostReport(window, backend, engine);
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    private void runCostReport(Window window, OpenGlRenderBackend backend, EpysiaEngine engine) {
        Scene scene = new Scene("shadow cost");
        MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
        Camera3D camera = populate(scene, BENCHMARK_BLOCKS);
        Transform3D cameraTransform = camera.owner().orElseThrow().getComponent(Transform3D.class).orElseThrow();
        Transform3D mover = moverOf(scene);
        long cascadeNanos = 0L;
        int scrolls = 0;
        int rebuilds = 0;
        for (int frame = 0; frame < BENCHMARK_WARMUP_FRAMES + BENCHMARK_FRAMES; frame++) {
            cameraTransform.setPosition(frame * CAMERA_STEP, 6.0f, 12.0f);
            mover.setPosition(2.0f, 0.5f + 0.01f * frame, 0.0f);
            renderFrame(engine, camera);
            if (frame < BENCHMARK_WARMUP_FRAMES) {
                continue;
            }
            cascadeNanos += backend.latestProfileTimingsNanos().getOrDefault("SHADOW_CASCADES", 0L);
            scrolls += meshes.shadowStatistics().staticLayersScrolled();
            rebuilds += meshes.shadowStatistics().staticLayersRebuilt();
        }
        System.out.printf("shadow cost report: blocks=%d, scroll=%s, SHADOW_CASCADES %.3f ms/frame, scrolled %d, rebuilt %d%n",
                BENCHMARK_BLOCKS, System.getProperty("epysia.shadow.scroll", "true"),
                cascadeNanos / (double) BENCHMARK_FRAMES / 1.0e6, scrolls, rebuilds);
    }

    @Test
    void reportsShadowCostOnProjectScene() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the project shadow cost report");
        String root = System.getProperty("epysia.shadow.benchmarkProject", "");
        String scenePath = System.getProperty("epysia.shadow.benchmarkScene", "");
        Assumptions.assumeTrue(!root.isBlank() && !scenePath.isBlank(),
                "-Depysia.shadow.benchmarkProject and -Depysia.shadow.benchmarkScene to run this report");
        System.setProperty("epysia.offscreen", "true");
        Window window = new Window("project shadow cost", 1280, 720);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            runProjectReport(window, backend, engine, Path.of(root), Path.of(scenePath));
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    private void runProjectReport(Window window, OpenGlRenderBackend backend, EpysiaEngine engine,
                                  Path projectRoot, Path scenePath) {
        Scene scene = new Scene("project shadow cost");
        MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
        loadProjectScene(engine, scene, projectRoot, scenePath);
        engine.beginPlay();
        Camera3D camera = scene.componentsOf(Camera3D.class).stream().findFirst().orElseThrow();
        Transform3D cameraTransform = camera.owner().orElseThrow().getComponent(Transform3D.class).orElseThrow();
        float startX = cameraTransform.position().x;
        float startY = cameraTransform.position().y;
        float startZ = cameraTransform.position().z;
        long cascadeNanos = 0L;
        int scrolls = 0;
        int rebuilds = 0;
        int casters = 0;
        for (int frame = 0; frame < BENCHMARK_WARMUP_FRAMES + BENCHMARK_FRAMES; frame++) {
            cameraTransform.setPosition(startX + frame * CAMERA_STEP, startY, startZ);
            renderFrame(engine, camera);
            if (frame < BENCHMARK_WARMUP_FRAMES) {
                continue;
            }
            cascadeNanos += backend.latestProfileTimingsNanos().getOrDefault("SHADOW_CASCADES", 0L);
            scrolls += meshes.shadowStatistics().staticLayersScrolled();
            rebuilds += meshes.shadowStatistics().staticLayersRebuilt();
            casters += meshes.shadowStatistics().castersSubmitted();
        }
        System.out.printf("project shadow cost: scene=%s, scroll=%s, SHADOW_CASCADES %.3f ms/frame,"
                        + " scrolled %d, rebuilt %d, objects %d, casters/frame %d%n",
                scenePath.getFileName(), System.getProperty("epysia.shadow.scroll", "false"),
                cascadeNanos / (double) BENCHMARK_FRAMES / 1.0e6, scrolls, rebuilds,
                scene.gameObjects().size(), casters / BENCHMARK_FRAMES);
    }

    private static void loadProjectScene(EpysiaEngine engine, Scene scene, Path projectRoot, Path scenePath) {
        ComponentRegistry registry = new ComponentRegistry();
        registry.populateFromScan(ComponentScanner.scan());
        ScriptLoadResult scripts = ScriptModule.load(projectRoot.resolve(Project.SCRIPTS_DIRECTORY_NAME),
                projectRoot.resolve(".epysia/scripts-out"), ProjectLibraries.forProjectRoot(projectRoot));
        registry.setUserComponents(scripts.components());
        engine.assets().attachProject(projectRoot);
        try {
            new SceneSerializer(registry).load(scene, scenePath, engine);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("could not load " + scenePath, error);
        }
        scene.advanceTick();
    }

    private MeshRenderSystem startEngine(Window window, OpenGlRenderBackend backend, EpysiaEngine engine,
                                         Scene scene) {
        engine.addScene(scene);
        ShaderLoader shaderLoader = ShaderLoader.autoDetect();
        MeshRenderSystem meshes = new MeshRenderSystem(shaderLoader, new ShaderWatcher(shaderLoader.filesystemRoot()),
                engine.logger());
        engine.addRenderSystem(meshes);
        window.open();
        backend.initialize(window);
        engine.initialize();
        engine.assets().register(new MeshAssetLoader(BuiltinMeshes.uploadAll(backend)));
        return meshes;
    }

    private static Transform3D moverOf(Scene scene) {
        return scene.gameObjects().stream()
                .filter(object -> object.name().equals("Mover")).findFirst().orElseThrow()
                .getComponent(Transform3D.class).orElseThrow();
    }

    private void runCheck(Window window, OpenGlRenderBackend backend, EpysiaEngine engine) {
        Scene scene = new Scene("shadow scroll");
        MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
        Camera3D camera = populate(scene, 8);
        Transform3D cameraTransform = camera.owner().orElseThrow().getComponent(Transform3D.class).orElseThrow();
        Transform3D mover = moverOf(scene);
        driveFrames(engine, meshes, camera, cameraTransform, mover);
        float[] scrolled = readCascades(backend, meshes);
        meshes.setShadowSplitEnabled(false);
        meshes.setShadowSplitEnabled(true);
        renderFrame(engine, camera);
        float[] rebuilt = readCascades(backend, meshes);
        assertTrue(observedScrolls > 0, "no static layer was ever scrolled, the check proves nothing");
        compare(scrolled, rebuilt);
    }

    private void driveFrames(EpysiaEngine engine, MeshRenderSystem meshes, Camera3D camera,
                             Transform3D cameraTransform, Transform3D mover) {
        int scrolls = 0;
        int rebuilds = 0;
        for (int frame = 0; frame < MOVING_FRAMES + SETTLING_FRAMES; frame++) {
            float travelled = Math.min(frame, MOVING_FRAMES) * CAMERA_STEP;
            cameraTransform.setPosition(travelled, 4.0f, 9.0f);
            mover.setPosition(2.0f, 0.5f + 0.01f * frame, 0.0f);
            renderFrame(engine, camera);
            scrolls += meshes.shadowStatistics().staticLayersScrolled();
            rebuilds += meshes.shadowStatistics().staticLayersRebuilt();
        }
        System.out.println("shadow scroll check: scroll=" + System.getProperty("epysia.shadow.scroll", "true")
                + ", static layers scrolled " + scrolls + ", rebuilt " + rebuilds);
        this.observedScrolls = scrolls;
    }

    private void renderFrame(EpysiaEngine engine, Camera3D camera) {
        engine.tick(input, 1.0f / 60.0f);
        engine.render(List.of(camera), RenderTargetHandle.SCREEN, 1.0f);
    }

    private static Camera3D populate(Scene scene, int blockCount) {
        GameObject cameraObject = new GameObject("Camera");
        cameraObject.addComponent(new Transform3D().setPosition(0.0f, 4.0f, 9.0f));
        Camera3D camera = cameraObject.addComponent(new Camera3D().setNearFar(0.1f, 60.0f));
        scene.addGameObject(cameraObject);
        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D()).lookAt(-0.5f, -1.0f, -0.35f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(new DirectionalLight().setIntensity(3.0f));
        scene.addGameObject(sun);
        scene.addGameObject(box("Ground", 0.0f, -0.5f, 0.0f, 40.0f, 1.0f, 40.0f));
        for (int index = 0; index < blockCount; index++) {
            scene.addGameObject(box("Block" + index, -6.0f + index % 90 * 0.5f, 0.75f,
                    index / 90 * 0.5f - 6.0f, 0.4f, 1.5f + index % 5 * 0.2f, 0.4f));
        }
        scene.addGameObject(box("Mover", 2.0f, 0.5f, 0.0f, 0.8f, 0.8f, 0.8f));
        scene.advanceTick();
        return camera;
    }

    private static GameObject box(String name, float x, float y, float z,
                                  float width, float height, float depth) {
        GameObject object = new GameObject(name);
        object.addComponent(new Transform3D().setPosition(x, y, z).setScale(width, height, depth));
        object.addComponent(new MeshRenderer())
                .setMeshPath("preset:cube")
                .setMaterial(new LitMaterial().setBaseColor(0.7f, 0.7f, 0.7f));
        return object;
    }

    private static float[] readCascades(OpenGlRenderBackend backend, MeshRenderSystem meshes) {
        int texelCount = CascadedShadowMaps.SHADOW_MAP_SIZE * CascadedShadowMaps.SHADOW_MAP_SIZE
                * CascadedShadowMaps.CASCADE_COUNT;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(texelCount);
        backend.readTextureLevel(meshes.shadowCascadeTexture(), 0, buffer);
        float[] depths = new float[texelCount];
        buffer.get(depths);
        return depths;
    }

    private static void compare(float[] scrolled, float[] rebuilt) {
        int mismatches = 0;
        float worst = 0.0f;
        for (int index = 0; index < scrolled.length; index++) {
            float difference = Math.abs(scrolled[index] - rebuilt[index]);
            worst = Math.max(worst, difference);
            if (difference > DEPTH_TOLERANCE) {
                mismatches++;
            }
        }
        double fraction = mismatches / (double) scrolled.length;
        System.out.printf("shadow scroll check: %d/%d texels differ (%.4f%%), worst %.6f%n",
                mismatches, scrolled.length, fraction * 100.0, worst);
        assertTrue(fraction < MISMATCH_LIMIT,
                String.format("scrolled cascades differ from a full rebuild on %.4f%% of texels", fraction * 100.0));
    }

    private static boolean displayAvailable() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }
}
