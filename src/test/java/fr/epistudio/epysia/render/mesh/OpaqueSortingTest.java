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
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.window.Window;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class OpaqueSortingTest {
    private static final int LAYERS = Integer.getInteger("epysia.sort.benchmarkLayers", 120);
    private static final int MATERIALS = 24;
    private static final int WARMUP_FRAMES = 60;
    private static final int MEASURED_FRAMES = 200;
    private static final float LAYER_SPACING = 0.4f;

    private final MutableInputState input = new MutableInputState();

    @Test
    void reportsOverdrawCost() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the opaque sorting report");
        Assumptions.assumeTrue(Boolean.getBoolean("epysia.sort.benchmark"),
                "-Depysia.sort.benchmark=true to run the opaque sorting report");
        System.setProperty("epysia.offscreen", "true");
        System.setProperty("epysia.gpu.profiling", "true");
        for (int round = 0; round < 3; round++) {
            report("material first", false, false);
            report("front to back", true, false);
            report("prepass", false, true);
            report("prepass front to back", true, true);
        }
    }

    private void report(String label, boolean frontToBack, boolean depthPrepass) {
        Window window = new Window("opaque sorting report", 1280, 720);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            Scene scene = new Scene("opaque sorting");
            MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
            meshes.setFrontToBackOpaque(frontToBack);
            meshes.setDepthPrepassEnabled(depthPrepass);
            Camera3D camera = populate(scene, backend);
            drive(engine, camera, WARMUP_FRAMES);
            long opaqueNanos = 0L;
            for (int frame = 0; frame < MEASURED_FRAMES; frame++) {
                drive(engine, camera, 1);
                opaqueNanos += backend.latestProfileTimingsNanos().getOrDefault("OPAQUE_3D", 0L);
            }
            System.out.printf("opaque sorting report: %-22s OPAQUE_3D %.3f ms/frame, %d draws,"
                            + " %d pipeline switches%n",
                    label, opaqueNanos / (double) MEASURED_FRAMES / 1.0e6,
                    backend.drawStatistics().drawCalls(), backend.drawStatistics().pipelineSwitches());
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    private void drive(EpysiaEngine engine, Camera3D camera, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            engine.tick(input, 1.0f / 60.0f);
            engine.render(List.of(camera), RenderTargetHandle.SCREEN, 1.0f);
        }
    }

    private MeshRenderSystem startEngine(Window window, OpenGlRenderBackend backend, EpysiaEngine engine,
                                         Scene scene) {
        engine.addScene(scene);
        ShaderLoader shaderLoader = ShaderLoader.autoDetect();
        MeshRenderSystem meshes = new MeshRenderSystem(shaderLoader,
                new ShaderWatcher(shaderLoader.filesystemRoot()), engine.logger());
        engine.addRenderSystem(meshes);
        window.open();
        backend.initialize(window);
        engine.initialize();
        engine.assets().register(new MeshAssetLoader(BuiltinMeshes.uploadAll(backend)));
        return meshes;
    }

    private static Camera3D populate(Scene scene, OpenGlRenderBackend backend) {
        GameObject cameraObject = new GameObject("Camera");
        cameraObject.addComponent(new Transform3D().setPosition(0.0f, 0.0f, 4.0f));
        Camera3D camera = cameraObject.addComponent(new Camera3D().setNearFar(0.1f, 200.0f));
        scene.addGameObject(cameraObject);
        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D()).lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(new DirectionalLight().setIntensity(3.0f));
        scene.addGameObject(sun);
        for (int layer = 0; layer < LAYERS; layer++) {
            scene.addGameObject(wall(layer, MeshUploader.upload(backend, quad(), Optional.empty())));
        }
        scene.advanceTick();
        return camera;
    }

    private static GameObject wall(int layer, UploadedMesh mesh) {
        GameObject object = new GameObject("Wall" + layer);
        object.addComponent(new Transform3D()
                .setPosition(0.0f, 0.0f, -layer * LAYER_SPACING)
                .setScale(20.0f, 20.0f, 0.05f));
        float shade = (layer % MATERIALS) / (float) MATERIALS;
        object.addComponent(new MeshRenderer())
                .setMesh(mesh)
                .setMaterial(new LitMaterial().setBaseColor(shade, 0.5f, 1.0f - shade)
                        .setRoughness(0.1f + shade * 0.8f));
        return object;
    }

    private static MeshData quad() {
        return new MeshData(
                new float[]{-1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0},
                new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1},
                new float[]{0, 0, 1, 0, 1, 1, 0, 1},
                new float[]{1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0},
                new short[0],
                new float[0],
                new int[]{0, 1, 2, 0, 2, 3},
                List.of());
    }

    private static boolean displayAvailable() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }
}
