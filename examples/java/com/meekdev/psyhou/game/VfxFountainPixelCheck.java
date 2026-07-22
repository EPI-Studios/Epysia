package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.DirectionalLight;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.FrameBuilder;
import fr.epistudio.epysia.render.RenderContext;
import fr.epistudio.epysia.render.RenderSystem;
import fr.epistudio.epysia.render.StageConfigurer;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.material.LitMaterial;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.PlaneMesh;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;
import fr.epistudio.epysia.vfx.ParticleEffect;

public final class VfxFountainPixelCheck {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int CHECK_FRAME = 30;
    private static final int SCAN_HALF_EXTENT = 60;
    private static final int RED_MARGIN = 40;

    private VfxFountainPixelCheck() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("VfxFountainPixelCheck", WIDTH, HEIGHT,
                VfxFountainPixelCheck::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        scene.addGameObject(buildCamera());
        scene.addGameObject(buildSun());
        scene.addGameObject(buildGround(services));
        scene.addGameObject(buildFountain());
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

    private static GameObject buildSun() {
        GameObject sun = new GameObject("sun");
        Transform3D transform = new Transform3D();
        transform.lookAt(-0.4f, -1.0f, -0.3f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(transform);
        sun.addComponent(new DirectionalLight().setColor(1.0f, 1.0f, 1.0f).setIntensity(2.0f)
                .setAmbient(0.15f, 0.15f, 0.15f));
        return sun;
    }

    private static GameObject buildGround(EngineServices services) {
        GameObject ground = new GameObject("ground");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, -2.0f, -6.0f);
        ground.addComponent(transform);
        LitMaterial material = new LitMaterial();
        material.setBaseColor(0.2f, 0.8f, 0.2f);
        ground.addComponent(new MeshRenderer()
                .setMesh(MeshUploader.upload(services.renderBackend(), PlaneMesh.data(8.0f, 1.0f)))
                .setMaterial(material));
        return ground;
    }

    private static GameObject buildFountain() {
        GameObject fountain = new GameObject("fountain");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, -1.0f, -6.0f);
        fountain.addComponent(transform);
        fountain.addComponent(new ParticleEffect().setPoolSize(512).setEmissionRate(200.0f));
        return fountain;
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
            report(warmPixelCount(), groundPixelGreen());
        }

        private int warmPixelCount() {
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            int background = backend.readPixelArgb(postProcess.sceneTarget(), 10, 10);
            int backgroundRed = background >> 16 & 0xFF;
            int warm = 0;
            for (int offsetY = -SCAN_HALF_EXTENT; offsetY <= SCAN_HALF_EXTENT; offsetY += 4) {
                for (int offsetX = -SCAN_HALF_EXTENT; offsetX <= SCAN_HALF_EXTENT; offsetX += 4) {
                    int pixel = backend.readPixelArgb(postProcess.sceneTarget(),
                            WIDTH / 2 + offsetX, HEIGHT / 2 + offsetY);
                    if ((pixel >> 16 & 0xFF) > backgroundRed + RED_MARGIN) {
                        warm++;
                    }
                }
            }
            return warm;
        }

        private int groundPixelGreen() {
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            int pixel = backend.readPixelArgb(postProcess.sceneTarget(), WIDTH / 2, HEIGHT - 20);
            return pixel >> 8 & 0xFF;
        }

        private static void report(int warmPixels, int groundGreen) {
            System.out.println("[vfx-check] warm pixels: " + warmPixels + " groundGreen: " + groundGreen);
            if (warmPixels > 3 && groundGreen > 40) {
                System.out.println("[vfx-check] PASS: fountain particles visible and ground still lit");
                System.exit(0);
            }
            System.out.println("[vfx-check] FAIL: particles=" + warmPixels + " groundGreen=" + groundGreen);
            System.exit(1);
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }
}
