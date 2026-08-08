package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.animation.Clip;
import fr.epistudio.epysia.animation.ClipChannel;
import fr.epistudio.epysia.animation.ClipInterpolation;
import fr.epistudio.epysia.animation.ClipProperty;
import fr.epistudio.epysia.animation.Joint;
import fr.epistudio.epysia.animation.Skeleton;
import fr.epistudio.epysia.components.Animator;
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
import org.joml.Matrix4f;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationScheduleTest {
    private static final int JOINTS = Integer.getInteger("epysia.animation.benchmarkJoints", 6);
    private static final float SEGMENT_HEIGHT = 0.25f;
    private static final int VISIBLE_COUNT = 24;
    private static final float FAR_AWAY_HEIGHT = 4000.0f;
    private static final int WARMUP_FRAMES = 3;
    private static final int SETTLING_FRAMES = 8;
    private static final int BENCHMARK_CHARACTERS =
            Integer.getInteger("epysia.animation.benchmarkCharacters", 400);
    private static final int BENCHMARK_FRAMES = 240;
    private static final int BENCHMARK_ROUNDS = 3;
    private static final int PARALLEL_CHECK_CHARACTERS = 200;

    private final MutableInputState input = new MutableInputState();

    @Test
    void animatorsBehindTheCameraStopSampling() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the animation culling check");
        System.setProperty("epysia.offscreen", "true");
        Window window = new Window("animation culling check", 640, 480);
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
        Scene scene = new Scene("animation culling");
        MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
        Camera3D camera = populate(scene, backend, VISIBLE_COUNT);
        Transform3D cameraTransform = camera.owner().orElseThrow()
                .getComponent(Transform3D.class).orElseThrow();
        drive(engine, scene, camera, WARMUP_FRAMES);
        assertEquals(VISIBLE_COUNT + 1, meshes.indexedEntryCount(),
                "skinned renderers did not enter the scene index");
        long sampledWhileVisible = meshes.posesSampled();
        assertTrue(sampledWhileVisible >= VISIBLE_COUNT,
                "the characters never sampled while visible, the check proves nothing");

        cameraTransform.setPosition(0.0f, FAR_AWAY_HEIGHT, 0.0f);
        drive(engine, scene, camera, SETTLING_FRAMES);
        long settled = meshes.posesSampled();
        drive(engine, scene, camera, WARMUP_FRAMES);
        long sampledWhileAway = meshes.posesSampled() - settled;
        int culled = meshes.animationsCulledThisFrame();
        meshes.setAnimationCullingEnabled(false);
        drive(engine, scene, camera, 1);
        int culledWithoutPolicy = meshes.animationsCulledThisFrame();
        System.out.printf("animation culling check: %d poses while visible, %d after moving away,"
                        + " %d culled this frame, %d with the policy off%n",
                sampledWhileVisible, sampledWhileAway, culled, culledWithoutPolicy);
        assertEquals(0, sampledWhileAway, "characters out of every frustum were still sampled");
        assertTrue(culled >= VISIBLE_COUNT, "the culling counter did not report the skipped poses");
        assertEquals(0, culledWithoutPolicy, "culling kept running after the policy was turned off");
    }

    @Test
    void aSecondRenderOfTheSameFrameDoesNotResample() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the resample check");
        System.setProperty("epysia.offscreen", "true");
        Window window = new Window("animation resample check", 640, 480);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            Scene scene = new Scene("animation resample");
            MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
            Camera3D camera = populate(scene, backend, 0);
            drive(engine, scene, camera, WARMUP_FRAMES);
            long afterFirstRender = meshes.posesSampled();
            engine.render(List.of(camera), RenderTargetHandle.SCREEN, 1.0f);
            assertEquals(afterFirstRender, meshes.posesSampled(),
                    "a second render inside the same frame sampled the pose again");
            engine.advanceAnimators(1.0f / 60.0f);
            engine.render(List.of(camera), RenderTargetHandle.SCREEN, 1.0f);
            assertTrue(meshes.posesSampled() > afterFirstRender,
                    "the pose stopped being sampled after the clock advanced");
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    @Test
    void parallelSamplingMatchesSerialSampling() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the parallel pose check");
        System.setProperty("epysia.offscreen", "true");
        float[] serial = samplePalettes(false);
        float[] parallel = samplePalettes(true);
        int mismatches = 0;
        for (int index = 0; index < serial.length; index++) {
            if (Math.abs(serial[index] - parallel[index]) > 1.0e-6f) {
                mismatches++;
            }
        }
        System.out.printf("parallel pose check: %d palette floats, %d differ%n", serial.length, mismatches);
        assertTrue(serial.length > 0, "no palette was read back, the check proves nothing");
        assertEquals(0, mismatches, "parallel sampling produced a different pose");
    }

    private float[] samplePalettes(boolean parallel) {
        Window window = new Window("parallel pose check", 640, 480);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            Scene scene = new Scene("parallel pose");
            MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
            meshes.setParallelAnimation(parallel);
            meshes.setAnimationFullRateDistance(0.0f);
            Camera3D camera = populate(scene, backend, PARALLEL_CHECK_CHARACTERS);
            drive(engine, scene, camera, WARMUP_FRAMES);
            return readPalettes(backend, meshes, scene);
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    private static float[] readPalettes(OpenGlRenderBackend backend, MeshRenderSystem meshes, Scene scene) {
        int paletteBytes = JOINTS * MeshShaderBindings.JOINT_PALETTE_BYTES_PER_JOINT;
        List<MeshRenderer> renderers = scene.componentsOf(MeshRenderer.class);
        float[] values = new float[renderers.size() * paletteBytes / Float.BYTES];
        ByteBuffer bytes = BufferUtils.createByteBuffer(paletteBytes);
        int offset = 0;
        for (MeshRenderer renderer : renderers) {
            bytes.clear();
            backend.readBuffer(meshes.jointPaletteBinding(renderer).orElseThrow().buffer(), bytes, 0L);
            bytes.order(ByteOrder.nativeOrder()).asFloatBuffer()
                    .get(values, offset, paletteBytes / Float.BYTES);
            offset += paletteBytes / Float.BYTES;
        }
        return values;
    }

    @Test
    void reportsAnimationCost() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the animation cost report");
        Assumptions.assumeTrue(Boolean.getBoolean("epysia.animation.benchmark"),
                "-Depysia.animation.benchmark=true to run the animation cost report");
        System.setProperty("epysia.offscreen", "true");
        for (int round = 0; round < BENCHMARK_ROUNDS; round++) {
            long serial = runCostReport(false);
            long parallel = runCostReport(true);
            System.out.printf("animation cost report: round %d, %d characters, %d joints, %d frames,"
                            + " serial %d ms, parallel %d ms%n",
                    round, BENCHMARK_CHARACTERS, JOINTS, BENCHMARK_FRAMES, serial, parallel);
        }
    }

    private long runCostReport(boolean parallel) {
        Window window = new Window("animation cost report", 1280, 720);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            Scene scene = new Scene("animation cost");
            MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
            meshes.setParallelAnimation(parallel);
            meshes.setAnimationFullRateDistance(0.0f);
            Camera3D camera = populate(scene, backend, BENCHMARK_CHARACTERS);
            drive(engine, scene, camera, WARMUP_FRAMES);
            long start = System.nanoTime();
            drive(engine, scene, camera, BENCHMARK_FRAMES);
            return (System.nanoTime() - start) / 1_000_000L;
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    private void drive(EpysiaEngine engine, Scene scene, Camera3D camera, int frames) {
        Clip clip = bendClip(chainSkeleton());
        for (int frame = 0; frame < frames; frame++) {
            for (Animator animator : scene.componentsOf(Animator.class)) {
                animator.assignClip("memory:bend", clip);
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
        return meshes;
    }

    private static Camera3D populate(Scene scene, OpenGlRenderBackend backend, int count) {
        GameObject cameraObject = new GameObject("Camera");
        cameraObject.addComponent(new Transform3D().setPosition(0.0f, 1.0f, 0.0f));
        Camera3D camera = cameraObject.addComponent(new Camera3D().setNearFar(0.1f, 60.0f));
        scene.addGameObject(cameraObject);
        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D()).lookAt(-0.5f, -1.0f, -0.35f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(new DirectionalLight().setIntensity(3.0f));
        scene.addGameObject(sun);
        Skeleton skeleton = chainSkeleton();
        UploadedMesh mesh = MeshUploader.upload(backend, skinnedBar(), Optional.of(skeleton));
        scene.addGameObject(animatedBar("Visible", mesh, skeleton, 0.0f, -6.0f));
        for (int index = 0; index < count; index++) {
            scene.addGameObject(animatedBar("Character" + index, mesh, skeleton,
                    index % 8 * 1.5f - 6.0f, -8.0f - index / 8 * 1.5f));
        }
        scene.advanceTick();
        return camera;
    }

    private static GameObject animatedBar(String name, UploadedMesh mesh, Skeleton skeleton, float x, float z) {
        GameObject object = new GameObject(name);
        object.addComponent(new Transform3D().setPosition(x, 0.0f, z));
        object.addComponent(new MeshRenderer())
                .setMesh(mesh)
                .setMaterial(new LitMaterial().setBaseColor(0.7f, 0.7f, 0.7f));
        object.addComponent(new Animator()).assignClip("memory:bend", bendClip(skeleton));
        return object;
    }

    private static Skeleton chainSkeleton() {
        List<Joint> joints = new ArrayList<>(JOINTS);
        for (int index = 0; index < JOINTS; index++) {
            float height = index == 0 ? 0.0f : SEGMENT_HEIGHT;
            joints.add(new Joint("joint" + index, index - 1,
                    matrixFloats(new Matrix4f().translation(0.0f, height, 0.0f)),
                    matrixFloats(new Matrix4f().translation(0.0f, -index * SEGMENT_HEIGHT, 0.0f))));
        }
        return new Skeleton(joints);
    }

    private static float[] matrixFloats(Matrix4f matrix) {
        float[] values = new float[16];
        matrix.get(values);
        return values;
    }

    private static Clip bendClip(Skeleton skeleton) {
        List<ClipChannel> channels = new ArrayList<>();
        for (int joint = 1; joint < skeleton.jointCount(); joint++) {
            channels.add(new ClipChannel(joint, ClipProperty.ROTATION, ClipInterpolation.LINEAR,
                    new float[]{0.0f, 1.0f},
                    new float[]{0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.2588f, 0.9659f}));
        }
        return new Clip("bend", 1.0f, skeleton.nameChecksum(), channels);
    }

    private static MeshData skinnedBar() {
        int vertexCount = JOINTS * 2;
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];
        float[] tangents = new float[vertexCount * 3];
        short[] jointIndices = new short[vertexCount * MeshData.INFLUENCES_PER_VERTEX];
        float[] jointWeights = new float[vertexCount * MeshData.INFLUENCES_PER_VERTEX];
        for (int ring = 0; ring < JOINTS; ring++) {
            for (int side = 0; side < 2; side++) {
                int vertex = ring * 2 + side;
                positions[vertex * 3] = side == 0 ? -0.25f : 0.25f;
                positions[vertex * 3 + 1] = ring * SEGMENT_HEIGHT;
                normals[vertex * 3 + 2] = 1.0f;
                tangents[vertex * 3] = 1.0f;
                jointIndices[vertex * MeshData.INFLUENCES_PER_VERTEX] = (short) ring;
                jointWeights[vertex * MeshData.INFLUENCES_PER_VERTEX] = 1.0f;
            }
        }
        return new MeshData(positions, normals, uvs, tangents, jointIndices, jointWeights,
                stripIndices(), List.of());
    }

    private static int[] stripIndices() {
        List<Integer> indices = new ArrayList<>();
        for (int ring = 0; ring + 1 < JOINTS; ring++) {
            int low = ring * 2;
            indices.add(low);
            indices.add(low + 2);
            indices.add(low + 1);
            indices.add(low + 1);
            indices.add(low + 2);
            indices.add(low + 3);
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean displayAvailable() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }
}
