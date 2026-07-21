package com.meekdev.psyhou.game;

import fr.epistudio.epysia.EngineServices;
import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.StandaloneRunner;
import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipChannel;
import fr.epistudio.epysia.animation.ClipInterpolation;
import fr.epistudio.epysia.animation.ClipProperty;
import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.assets.epyclip.EpyClipWriter;
import fr.epistudio.epysia.components.Animator;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.MeshRenderer;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class SkinnedAnimationPixelCheck {

    private static final String SURFACE_SHADER = "example/instance_check.surf.glsl";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int SAMPLE_Y = 180;
    private static final int SAMPLE_STRIDE = 2;
    private static final int WARMUP_FRAMES = 5;
    private static final long LATE_SAMPLE_DELAY_NANOS = 400_000_000L;
    private static final int MAXIMUM_FRAMES = 1200;
    private static final int RED_DOMINANCE_MARGIN = 60;
    private static final float MINIMUM_CENTROID_SHIFT = 40.0f;
    private static final int TIP_JOINT_INDEX = 1;
    private static final float TRANSLATION_DISTANCE = 3.0f;
    private static final float CLIP_DURATION_SECONDS = 1.0f;

    private SkinnedAnimationPixelCheck() {
    }

    public static void main(String[] arguments) {
        StandaloneRunner.runStandalone("SkinnedAnimationPixelCheck", WIDTH, HEIGHT,
                SkinnedAnimationPixelCheck::populate);
    }

    private static void populate(EpysiaEngine engine, EngineServices services) {
        Scene scene = engine.scene();
        RenderBackend backend = services.renderBackend();
        Skeleton skeleton = bindPoseSkeleton();
        UploadedMesh skinnedQuad = MeshUploader.upload(backend, skinnedQuadData(), Optional.of(skeleton));
        Path clipPath = writeClipFile(skeleton);
        scene.addGameObject(buildCamera());
        scene.addGameObject(buildQuad(skinnedQuad, clipPath));
        engine.addRenderSystem(new PixelCheckSystem(engine));
    }

    private static Path writeClipFile(Skeleton skeleton) {
        ClipChannel channel = new ClipChannel(TIP_JOINT_INDEX, ClipProperty.TRANSLATION, ClipInterpolation.LINEAR,
                new float[]{0.0f, CLIP_DURATION_SECONDS},
                new float[]{0.0f, 0.0f, 0.0f, TRANSLATION_DISTANCE, 0.0f, 0.0f});
        Clip clip = new Clip("tip-shift", CLIP_DURATION_SECONDS, skeleton.nameChecksum(), List.of(channel));
        try {
            Path path = Files.createTempFile("skinned-animation-check", ".epyclip");
            EpyClipWriter.writeToFile(path, clip);
            return path;
        } catch (IOException exception) {
            throw new EpysiaException("Failed to create temporary clip file: " + exception.getMessage(), exception);
        }
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
            jointIndices[vertex * 4] = TIP_JOINT_INDEX;
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

    private static GameObject buildQuad(UploadedMesh mesh, Path clipPath) {
        LitMaterial material = new LitMaterial();
        material.setSurfaceShaderPath(SURFACE_SHADER);
        material.setFloat("tintAmount", 1.0f);
        GameObject quad = new GameObject("skinned-quad");
        Transform3D transform = new Transform3D();
        transform.setPosition(0.0f, 0.0f, -6.0f);
        quad.addComponent(transform);
        quad.addComponent(new MeshRenderer().setMesh(mesh).setMaterial(material));
        quad.addComponent(new Animator().setClipPath(clipPath.toAbsolutePath().toString())
                .setLooping(true).setSpeed(1.0f));
        return quad;
    }

    private static final class PixelCheckSystem implements RenderSystem {

        private final EpysiaEngine engine;
        private RenderBackend backend;
        private int frameCount;
        private float earlyCentroid = -1.0f;
        private long earlyCaptureNanos = -1L;

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
            if (frameCount < WARMUP_FRAMES) {
                return;
            }
            if (earlyCaptureNanos < 0L) {
                captureEarly();
                return;
            }
            if (System.nanoTime() - earlyCaptureNanos < LATE_SAMPLE_DELAY_NANOS) {
                failIfExceededMaximumFrames();
                return;
            }
            float lateCentroid = redCentroidX();
            report(lateCentroid);
        }

        private void captureEarly() {
            earlyCentroid = redCentroidX();
            if (earlyCentroid >= 0.0f) {
                earlyCaptureNanos = System.nanoTime();
            }
        }

        private void failIfExceededMaximumFrames() {
            if (frameCount < MAXIMUM_FRAMES) {
                return;
            }
            System.out.println("[skinned-animation-check] FAIL: exceeded maximum frames waiting for late sample");
            System.exit(1);
        }

        private float redCentroidX() {
            PostProcessSystem postProcess = engine.renderSystem(PostProcessSystem.class);
            double weightedSum = 0.0;
            double totalWeight = 0.0;
            for (int x = 0; x < WIDTH; x += SAMPLE_STRIDE) {
                int pixel = backend.readPixelArgb(postProcess.sceneTarget(), x, SAMPLE_Y);
                if (!isRedDominant(pixel)) {
                    continue;
                }
                int red = pixel >> 16 & 0xFF;
                weightedSum += (double) x * red;
                totalWeight += red;
            }
            return totalWeight > 0.0 ? (float) (weightedSum / totalWeight) : -1.0f;
        }

        private void report(float lateCentroid) {
            System.out.printf("[skinned-animation-check] earlyCentroidX=%.2f lateCentroidX=%.2f%n",
                    earlyCentroid, lateCentroid);
            float shift = lateCentroid - earlyCentroid;
            if (earlyCentroid >= 0.0f && lateCentroid >= 0.0f && shift >= MINIMUM_CENTROID_SHIFT) {
                System.out.println("[skinned-animation-check] PASS: red centroid shifted right by "
                        + shift + " pixels");
                System.exit(0);
            }
            System.out.println("[skinned-animation-check] FAIL: red centroid did not shift as expected");
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
