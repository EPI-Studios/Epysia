package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.mesh.CubeMesh;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;

public final class RemovedObjectPixelCheck {

    private static final String SURFACE_SHADER = "example/instance_check.surf.glsl";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int BASELINE_FRAME = 4;
    private static final int REMOVE_FRAME = 5;
    private static final int VERIFY_FRAME = 12;
    private static final int CENTER_X = WIDTH / 2;
    private static final int CENTER_Y = HEIGHT / 2;

    private RemovedObjectPixelCheck() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("RemovedObjectPixelCheck", WIDTH, HEIGHT,
                RemovedObjectPixelCheck::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        RenderBackend backend = services.renderBackend();
        UploadedMesh cube = MeshUploader.upload(backend, CubeMesh.data());
        GameObject root = buildRoot();
        GameObject mesh = buildMeshChild(root, cube);
        scene.addGameObject(buildCamera());
        scene.addGameObject(root);
        scene.addGameObject(mesh);
        engine.addRenderSystem(new RemovalCheckSystem(engine, root));
    }

    private static GameObject buildCamera() {
        GameObject camera = new GameObject("camera");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.0f, 0.0f);
        transform.lookAt(0.0f, 0.0f, -6.0f, 0.0f, 1.0f, 0.0f);
        camera.addComponent(transform);
        camera.addComponent(new Camera3D().setActive(true)
                .setNearFar(0.1f, 100.0f).setFieldOfViewDegrees(65.0f));
        return camera;
    }

    private static GameObject buildRoot() {
        GameObject root = new GameObject("model-root");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.0f, -6.0f);
        root.addComponent(transform);
        return root;
    }

    private static GameObject buildMeshChild(GameObject root, UploadedMesh mesh) {
        LitMaterial material = new LitMaterial();
        material.setSurfaceShaderPath(SURFACE_SHADER);
        material.setFloat("tintAmount", 1.0f);
        GameObject child = new GameObject("model-mesh");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.0f, 0.0f);
        transform.setParent(root.getComponentOrNull(Transform3D.class));
        child.addComponent(transform);
        child.addComponent(new MeshRenderer().setMesh(mesh).setMaterial(material));
        return child;
    }

    private static boolean isRed(int pixel) {
        int red = pixel >> 16 & 0xFF;
        int green = pixel >> 8 & 0xFF;
        int blue = pixel & 0xFF;
        return red > 120 && red > green + 40 && red > blue + 40;
    }

    private static final class RemovalCheckSystem implements RenderSystem {

        private final EpysiaEngine engine;
        private final GameObject root;
        private RenderBackend backend;
        private int frameCount;

        private RemovalCheckSystem(EpysiaEngine engine, GameObject root) {
            this.engine = engine;
            this.root = root;
        }

        @Override
        public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
            this.backend = renderBackend;
        }

        @Override
        public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
            frameCount++;
            if (frameCount == BASELINE_FRAME) {
                verifyBaseline();
            } else if (frameCount == REMOVE_FRAME) {
                scene.removeGameObject(root);
                scene.advanceTick();
            } else if (frameCount == VERIFY_FRAME) {
                verifyGone();
            }
        }

        private void verifyBaseline() {
            int pixel = readCenter();
            System.out.printf("[removal-check] baseline center=%08x%n", pixel);
            if (!isRed(pixel)) {
                System.out.println("[removal-check] FAIL: cube never rendered red before removal");
                System.exit(1);
            }
        }

        private void verifyGone() {
            int pixel = readCenter();
            System.out.printf("[removal-check] after-removal center=%08x%n", pixel);
            if (isRed(pixel)) {
                System.out.println("[removal-check] FAIL: removed mesh keeps rendering (ghost)");
                System.exit(1);
            }
            System.out.println("[removal-check] PASS: removed subtree stopped rendering");
            System.exit(0);
        }

        private int readCenter() {
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            return backend.readPixelArgb(postProcess.sceneTarget(), CENTER_X, CENTER_Y);
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }
}
