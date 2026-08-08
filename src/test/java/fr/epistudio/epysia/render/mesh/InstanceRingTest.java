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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceRingTest {
    private static final int MOVERS = Integer.getInteger("epysia.ring.benchmarkMovers", 4000);
    private static final int WARMUP_FRAMES = 60;
    private static final int MEASURED_FRAMES = 300;
    private static final int ROUNDS = 3;
    private static final boolean DISTINCT_MESHES = Boolean.getBoolean("epysia.ring.distinctMeshes");

    private final MutableInputState input = new MutableInputState();

    @Test
    void movingInstancesRenderTheSameWithAndWithoutTheRing() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the instance ring check");
        System.setProperty("epysia.offscreen", "true");
        long single = trianglesWith(false);
        long ringed = trianglesWith(true);
        System.out.printf("instance ring check: %d triangles single buffered, %d ring buffered%n",
                single, ringed);
        assertTrue(single > 0, "nothing was drawn, the check proves nothing");
        assertEquals(single, ringed, "the ring changed what reaches the rasteriser");
    }

    @Test
    void perObjectTransformsShareArenaBlocks() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the transform arena check");
        Assumptions.assumeTrue(DISTINCT_MESHES,
                "-Depysia.ring.distinctMeshes=true to exercise the non batched path");
        System.setProperty("epysia.offscreen", "true");
        Window window = new Window("transform arena", 640, 480);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            Scene scene = new Scene("transform arena");
            MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
            List<Transform3D> movers = new ArrayList<>();
            Camera3D camera = populate(scene, backend, movers);
            drive(engine, camera, movers, 3);
            int blocks = meshes.objectUniformBlockCount();
            System.out.printf("transform arena check: %d renderers share %d buffers%n", MOVERS, blocks);
            assertTrue(blocks > 0, "no arena block was allocated, the check proves nothing");
            assertTrue(blocks < MOVERS / 100,
                    "the per object transforms did not collapse into arena blocks: " + blocks);
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    @Test
    void reportsInstanceUploadCost() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the instance ring report");
        Assumptions.assumeTrue(Boolean.getBoolean("epysia.ring.benchmark"),
                "-Depysia.ring.benchmark=true to run the instance ring report");
        System.setProperty("epysia.offscreen", "true");
        for (int round = 0; round < ROUNDS; round++) {
            long single = measure(false);
            long ringed = measure(true);
            System.out.printf("instance ring report: round %d, %d movers, %d frames,"
                            + " single buffered %d ms, ring buffered %d ms%n",
                    round, MOVERS, MEASURED_FRAMES, single, ringed);
        }
    }

    private long trianglesWith(boolean ringed) {
        return run(ringed, 8, false);
    }

    private long measure(boolean ringed) {
        return run(ringed, MEASURED_FRAMES, true);
    }

    private long run(boolean ringed, int frames, boolean timed) {
        Window window = new Window("instance ring", 1280, 720);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            Scene scene = new Scene("instance ring");
            MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
            meshes.setRingInstanceBuffers(ringed);
            List<Transform3D> movers = new ArrayList<>();
            Camera3D camera = populate(scene, backend, movers);
            drive(engine, camera, movers, timed ? WARMUP_FRAMES : 2);
            long start = System.nanoTime();
            drive(engine, camera, movers, frames);
            return timed ? (System.nanoTime() - start) / 1_000_000L : backend.drawStatistics().triangles();
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    private void drive(EpysiaEngine engine, Camera3D camera, List<Transform3D> movers, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            float wave = frame * 0.05f;
            for (int index = 0; index < movers.size(); index++) {
                Transform3D mover = movers.get(index);
                mover.setPosition(mover.position().x, (float) Math.sin(wave + index * 0.01f) * 2.0f,
                        mover.position().z);
            }
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

    private static Camera3D populate(Scene scene, OpenGlRenderBackend backend, List<Transform3D> movers) {
        GameObject cameraObject = new GameObject("Camera");
        cameraObject.addComponent(new Transform3D().setPosition(0.0f, 20.0f, 60.0f));
        Camera3D camera = cameraObject.addComponent(new Camera3D().setNearFar(0.1f, 300.0f));
        scene.addGameObject(cameraObject);
        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D()).lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(new DirectionalLight().setIntensity(3.0f));
        scene.addGameObject(sun);
        int side = (int) Math.ceil(Math.sqrt(MOVERS));
        for (int index = 0; index < MOVERS; index++) {
            GameObject object = new GameObject("Mover" + index);
            Transform3D transform = object.addComponent(new Transform3D()
                    .setPosition(index % side * 1.2f - side * 0.6f, 0.0f, index / side * 1.2f - side * 0.6f));
            MeshRenderer renderer = object.addComponent(new MeshRenderer())
                    .setMaterial(new LitMaterial().setBaseColor(0.6f, 0.7f, 0.8f));
            if (DISTINCT_MESHES) {
                renderer.setMesh(MeshUploader.upload(backend, quad(), Optional.empty()));
            } else {
                renderer.setMeshPath("preset:cube");
            }
            scene.addGameObject(object);
            movers.add(transform);
        }
        scene.advanceTick();
        return camera;
    }

    private static MeshData quad() {
        return new MeshData(
                new float[]{-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0},
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
