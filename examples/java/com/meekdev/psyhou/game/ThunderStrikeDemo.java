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

public final class ThunderStrikeDemo {

    public static final float DEPTH_Z = -11.0f;
    public static final float GROUND_Y = -4.2f;
    public static final float CAMERA_HEIGHT = 0.0f;
    public static final float FIELD_OF_VIEW_DEGREES = 60.0f;
    public static final float DURATION_SECONDS = 1.6f;
    public static final int POOL_SIZE = 4096;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final float GROUND_SIZE = 60.0f;

    private ThunderStrikeDemo() {
    }

    public static void main(String[] arguments) {
        VfxExampleGraphs.validateAll();
        StandaloneRunner.runStandalone("Epysia Thunder Strike", WIDTH, HEIGHT, ThunderStrikeDemo::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        scene.addGameObject(buildCamera());
        scene.addGameObject(buildSky());
        scene.addGameObject(buildGround(services));
        scene.addGameObject(buildBolt());
        engine.addRenderSystem(new BloomSetup(engine));
    }

    public static GameObject buildCamera() {
        GameObject camera = new GameObject("camera");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, CAMERA_HEIGHT, 0.0f);
        transform.lookAt(0.0f, CAMERA_HEIGHT, DEPTH_Z, 0.0f, 1.0f, 0.0f);
        camera.addComponent(transform);
        camera.addComponent(new Camera3D().setActive(true)
                .setNearFar(0.1f, 200.0f).setFieldOfViewDegrees(FIELD_OF_VIEW_DEGREES));
        return camera;
    }

    public static GameObject buildBolt() {
        GameObject bolt = new GameObject("ThunderStrike");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.0f, DEPTH_Z);
        bolt.addComponent(transform);
        bolt.addComponent(new ParticleEffect()
                .setPoolSize(POOL_SIZE)
                .setDuration(DURATION_SECONDS)
                .setLooping(true)
                .setGraphPath(VfxExampleGraphs.fileOf("ThunderStrike").toString()));
        return bolt;
    }

    public static final class BloomSetup implements RenderSystem {

        private static final float BLOOM_THRESHOLD = 1.4f;
        private static final float BLOOM_KNEE = 0.7f;
        private static final float BLOOM_INTENSITY = 0.22f;

        private final EpysiaEngine engine;
        private boolean applied;

        public BloomSetup(EpysiaEngine engine) {
            this.engine = engine;
        }

        @Override
        public void initialize(RenderBackend renderBackend, StageConfigurer configurer) {
        }

        @Override
        public void collect(Scene scene, FrameBuilder frame, RenderContext context) {
            if (applied || !engine.hasRenderSystem(PostProcessSystem.class)) {
                return;
            }
            applied = true;
            engine.renderSystem(PostProcessSystem.class).settings().setBloomEnabled(true)
                    .setBloom(BLOOM_THRESHOLD, BLOOM_KNEE, BLOOM_INTENSITY);
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }

    private static GameObject buildSky() {
        GameObject sky = new GameObject("sky");
        Transform3D transform = new Transform3D();
        transform.lookAt(-0.2f, -1.0f, -0.4f, 0.0f, 1.0f, 0.0f);
        sky.addComponent(transform);
        sky.addComponent(new DirectionalLight().setColor(0.35f, 0.4f, 0.6f).setIntensity(0.3f)
                .setAmbient(0.03f, 0.035f, 0.06f));
        return sky;
    }

    private static GameObject buildGround(EngineServices services) {
        GameObject ground = new GameObject("ground");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, GROUND_Y, DEPTH_Z);
        ground.addComponent(transform);
        LitMaterial material = new LitMaterial();
        material.setBaseColor(0.1f, 0.1f, 0.12f);
        material.setRoughness(0.95f);
        ground.addComponent(new MeshRenderer()
                .setMesh(MeshUploader.upload(services.renderBackend(), PlaneMesh.data(GROUND_SIZE, 1.0f)))
                .setMaterial(material));
        return ground;
    }
}
