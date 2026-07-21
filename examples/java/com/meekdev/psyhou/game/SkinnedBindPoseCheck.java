package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
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
import fr.epistudio.epysia.render.mesh.MeshData;
import fr.epistudio.epysia.render.mesh.MeshUploader;
import fr.epistudio.epysia.render.mesh.UploadedMesh;
import fr.epistudio.epysia.render.postfx.PostProcessSystem;
import fr.epistudio.epysia.scene.Scene;

import java.util.List;
import java.util.Optional;

public final class SkinnedBindPoseCheck {

    private static final String SURFACE_SHADER = "example/instance_check.surf.glsl";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int CHECK_FRAME = 5;
    private static final int CENTER_PIXEL_X = 320;
    private static final int CENTER_PIXEL_Y = 180;
    private static final int CORNER_PIXEL_X = 10;
    private static final int CORNER_PIXEL_Y = 10;
    private static final int RED_DOMINANCE_MARGIN = 60;

    private SkinnedBindPoseCheck() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("SkinnedBindPoseCheck", WIDTH, HEIGHT,
                SkinnedBindPoseCheck::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        RenderBackend backend = services.renderBackend();
        UploadedMesh skinnedQuad = MeshUploader.upload(backend, skinnedQuadData(), Optional.of(bindPoseSkeleton()));
        scene.addGameObject(buildCamera());
        scene.addGameObject(buildQuad(skinnedQuad));
        engine.addRenderSystem(new PixelCheckSystem(engine));
    }

    private static MeshData skinnedQuadData() {
        float[] positions = {
                -1.0f, -1.0f, 0.0f,
                1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                -1.0f, 1.0f, 0.0f
        };
        float[] normals = {
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f
        };
        float[] uvs = {
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        };
        int[] indices = {0, 1, 2, 0, 2, 3};
        short[] jointIndices = new short[16];
        float[] jointWeights = new float[16];
        for (int vertex = 0; vertex < 4; vertex++) {
            jointWeights[vertex * 4] = 1.0f;
        }
        return new MeshData(positions, normals, uvs, new float[0], jointIndices, jointWeights, indices, List.of());
    }

    private static Skeleton bindPoseSkeleton() {
        return new Skeleton(List.of(
                new Joint("root", -1, identityMatrix(), identityMatrix()),
                new Joint("tip", 0, identityMatrix(), identityMatrix())));
    }

    private static float[] identityMatrix() {
        float[] matrix = new float[16];
        matrix[0] = 1.0f;
        matrix[5] = 1.0f;
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
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

    private static GameObject buildQuad(UploadedMesh mesh) {
        LitMaterial material = new LitMaterial();
        material.setSurfaceShaderPath(SURFACE_SHADER);
        material.setFloat("tintAmount", 1.0f);
        GameObject quad = new GameObject("skinned-quad");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.0f, -6.0f);
        quad.addComponent(transform);
        quad.addComponent(new MeshRenderer().setMesh(mesh).setMaterial(material));
        return quad;
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
            int centerPixel = backend.readPixelArgb(postProcess.sceneTarget(), CENTER_PIXEL_X, CENTER_PIXEL_Y);
            int cornerPixel = backend.readPixelArgb(postProcess.sceneTarget(), CORNER_PIXEL_X, CORNER_PIXEL_Y);
            report(centerPixel, cornerPixel);
        }

        private static void report(int centerPixel, int cornerPixel) {
            boolean centerIsRed = isRedDominant(centerPixel);
            boolean cornerIsRed = isRedDominant(cornerPixel);
            System.out.printf("[skinned-check] center=%08x corner=%08x%n", centerPixel, cornerPixel);
            if (centerIsRed && !cornerIsRed) {
                System.out.println("[skinned-check] PASS: skinned quad rendered red at bind pose, background clear elsewhere");
                System.exit(0);
            }
            System.out.println("[skinned-check] FAIL: skinned quad did not render as expected");
            System.exit(1);
        }

        private static boolean isRedDominant(int pixel) {
            int red = pixel >> 16 & 0xFF;
            int green = pixel >> 8 & 0xFF;
            int blue = pixel & 0xFF;
            return red - green > RED_DOMINANCE_MARGIN && red - blue > RED_DOMINANCE_MARGIN;
        }

        @Override
        public void shutdown(RenderBackend renderBackend) {
        }
    }
}
