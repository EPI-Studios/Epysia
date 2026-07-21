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

public final class MaterialInstancePixelCheck {

    private static final String SURFACE_SHADER = "example/instance_check.surf.glsl";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int CHECK_FRAME = 5;
    private static final int LEFT_PIXEL_X = 226;
    private static final int RIGHT_PIXEL_X = 414;
    private static final int PIXEL_Y = HEIGHT / 2;

    private MaterialInstancePixelCheck() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("MaterialInstancePixelCheck", WIDTH, HEIGHT,
                MaterialInstancePixelCheck::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        RenderBackend backend = services.renderBackend();
        UploadedMesh cube = MeshUploader.upload(backend, CubeMesh.data());
        scene.addGameObject(buildCamera());
        scene.addGameObject(buildCube("left-cube", cube, -2.0f, 0.0f));
        scene.addGameObject(buildCube("right-cube", cube, 2.0f, 1.0f));
        engine.addRenderSystem(new PixelCheckSystem(engine));
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

    private static GameObject buildCube(String name, UploadedMesh mesh, float x, float tintAmount) {
        LitMaterial material = new LitMaterial();
        material.setSurfaceShaderPath(SURFACE_SHADER);
        material.setFloat("tintAmount", tintAmount);
        GameObject cube = new GameObject(name);
        Transform3D transform = new Transform3D();
        transform.setPosition(x, 0.0f, -6.0f);
        cube.addComponent(transform);
        cube.addComponent(new MeshRenderer().setMesh(mesh).setMaterial(material));
        return cube;
    }

    private static final class PixelCheckSystem implements RenderSystem {

        private final EpysiaEngine engine;
        private RenderBackend backend;
        private int frameCount;

        private PixelCheckSystem(EpysiaEngine engine) {
            this.engine = engine;
        }

        @Override
        public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
            this.backend = renderBackend;
        }

        @Override
        public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
            frameCount++;
            if (frameCount != CHECK_FRAME) {
                return;
            }
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            int leftPixel = backend.readPixelArgb(postProcess.sceneTarget(), LEFT_PIXEL_X, PIXEL_Y);
            int rightPixel = backend.readPixelArgb(postProcess.sceneTarget(), RIGHT_PIXEL_X, PIXEL_Y);
            report(leftPixel, rightPixel);
        }

        private static void report(int leftPixel, int rightPixel) {
            int leftRed = leftPixel >> 16 & 0xFF;
            int leftBlue = leftPixel & 0xFF;
            int rightRed = rightPixel >> 16 & 0xFF;
            int rightBlue = rightPixel & 0xFF;
            boolean leftIsBlue = leftBlue > leftRed;
            boolean rightIsRed = rightRed > rightBlue;
            System.out.printf("[pixel-check] left=%08x right=%08x%n", leftPixel, rightPixel);
            if (leftIsBlue && rightIsRed) {
                System.out.println("[pixel-check] PASS: instances of one shader rendered with distinct uniforms");
                System.exit(0);
            }
            System.out.println("[pixel-check] FAIL: both instances rendered with the same uniform values");
            System.exit(1);
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }
}
