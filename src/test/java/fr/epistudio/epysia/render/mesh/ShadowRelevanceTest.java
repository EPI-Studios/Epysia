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

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowRelevanceTest {
    private static final int NON_CASTERS = 12;
    private static final int FRAMES = 4;
    private static final float BEHIND_CAMERA_Z = 18.0f;

    private final MutableInputState input = new MutableInputState();

    @Test
    void offScreenNonCastersAreSkippedWhileCastersKeepTheirShadow() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the shadow relevance check");
        System.setProperty("epysia.offscreen", "true");
        Window window = new Window("shadow relevance check", 640, 480);
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

    private void runCheck(Window window, OpenGlRenderBackend backend, EpysiaEngine engine) {
        Scene scene = new Scene("shadow relevance");
        MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
        Camera3D camera = populate(scene);
        for (int frame = 0; frame < FRAMES; frame++) {
            engine.tick(input, 1.0f / 60.0f);
            engine.render(List.of(camera), RenderTargetHandle.SCREEN, 1.0f);
        }
        int skipped = meshes.skippedByRelevanceThisFrame();
        int casters = meshes.shadowStatistics().castersSubmitted();
        System.out.printf("shadow relevance check: %d non casters skipped of %d, %d casters submitted,"
                        + " index pruned %d, index active %b%n",
                skipped, NON_CASTERS, casters, meshes.skippedByIndexThisFrame(), meshes.sceneIndexEnabled());
        assertTrue(skipped >= NON_CASTERS,
                "off screen renderers that cast nothing were still submitted: " + skipped);
        assertTrue(casters > 0, "the off screen caster lost its shadow, that is the regression to avoid");
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

    private static Camera3D populate(Scene scene) {
        GameObject cameraObject = new GameObject("Camera");
        cameraObject.addComponent(new Transform3D().setPosition(0.0f, 3.0f, 0.0f));
        Camera3D camera = cameraObject.addComponent(new Camera3D().setNearFar(0.1f, 80.0f));
        scene.addGameObject(cameraObject);
        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D()).lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(new DirectionalLight().setIntensity(3.0f));
        scene.addGameObject(sun);
        scene.addGameObject(block("Caster", 0.0f, 2.0f, BEHIND_CAMERA_Z, true));
        for (int index = 0; index < NON_CASTERS; index++) {
            scene.addGameObject(block("NonCaster" + index, index * 1.5f - 8.0f, 2.0f,
                    BEHIND_CAMERA_Z + 2.0f, false));
        }
        scene.advanceTick();
        return camera;
    }

    private static GameObject block(String name, float x, float y, float z, boolean castsShadows) {
        GameObject object = new GameObject(name);
        object.addComponent(new Transform3D().setPosition(x, y, z));
        object.addComponent(new MeshRenderer())
                .setMeshPath("preset:cube")
                .setCastShadows(castsShadows)
                .setMaterial(new LitMaterial().setBaseColor(0.7f, 0.7f, 0.7f));
        return object;
    }

    private static boolean displayAvailable() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }
}
