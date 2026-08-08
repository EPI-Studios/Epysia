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
import fr.epistudio.epysia.render.backend.BufferHandle;
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
import java.nio.FloatBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinnedDeformationTest {
    private static final int SEGMENTS = 8;
    private static final int VERTICES_PER_RING = 4;
    private static final float BAR_HALF_WIDTH = 0.25f;
    private static final float SEGMENT_HEIGHT = 0.25f;
    private static final int FRAMES = 12;
    private static final float POSITION_TOLERANCE = 1.0e-4f;
    private static final float BEND_MINIMUM = 0.05f;
    private static final float DEPTH_TOLERANCE = 1.0e-4f;
    private static final double CASCADE_MISMATCH_LIMIT = 0.001;

    private final MutableInputState input = new MutableInputState();

    @Test
    void deformedVerticesMatchPaletteSkinning() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the skinned deformation check");
        System.setProperty("epysia.offscreen", "true");
        System.setProperty("epysia.render.skinOnce", "true");
        Window window = new Window("skinned deformation check", 640, 480);
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

    @Test
    void deformedShadowsMatchVertexShaderSkinning() {
        Assumptions.assumeTrue(displayAvailable(), "no display, skipping the skinned shadow check");
        System.setProperty("epysia.offscreen", "true");
        float[] vertexSkinned = renderCascades(false);
        float[] deformed = renderCascades(true);
        compareCascades(vertexSkinned, deformed);
    }

    private float[] renderCascades(boolean skinOnce) {
        System.setProperty("epysia.render.skinOnce", Boolean.toString(skinOnce));
        Window window = new Window("skinned shadow check", 640, 480);
        OpenGlRenderBackend backend = new OpenGlRenderBackend();
        EpysiaEngine engine = new EpysiaEngine(window, backend);
        try {
            Scene scene = new Scene("skinned shadows");
            MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
            MeshRenderer renderer = driveAnimatedBar(engine, scene, backend, meshes);
            assertEquals(skinOnce, meshes.deformedVertexBuffer(renderer).isPresent(),
                    "the deformation path did not follow epysia.render.skinOnce=" + skinOnce);
            float[] cascades = readCascades(backend, meshes);
            assertCasts(cascades);
            return cascades;
        } finally {
            engine.shutdown();
            backend.shutdown();
            window.close();
        }
    }

    private static void compareCascades(float[] vertexSkinned, float[] deformed) {
        int mismatches = 0;
        for (int index = 0; index < vertexSkinned.length; index++) {
            if (Math.abs(vertexSkinned[index] - deformed[index]) > DEPTH_TOLERANCE) {
                mismatches++;
            }
        }
        double fraction = mismatches / (double) vertexSkinned.length;
        System.out.printf("skinned shadow check: %d/%d texels differ (%.4f%%)%n",
                mismatches, vertexSkinned.length, fraction * 100.0);
        assertTrue(fraction < CASCADE_MISMATCH_LIMIT,
                String.format("deformed shadows differ from vertex shader skinning on %.4f%% of texels",
                        fraction * 100.0));
    }

    private static void assertCasts(float[] cascades) {
        int written = 0;
        for (float depth : cascades) {
            if (depth < 1.0f) {
                written++;
            }
        }
        assertTrue(written > 0, "the bar cast no shadow at all, the comparison proves nothing");
    }

    private static float[] readCascades(OpenGlRenderBackend backend, MeshRenderSystem meshes) {
        int texelCount = CascadedShadowMaps.SHADOW_MAP_SIZE * CascadedShadowMaps.SHADOW_MAP_SIZE
                * CascadedShadowMaps.CASCADE_COUNT;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(texelCount);
        backend.readTextureLevel(meshes.shadowCascadeTexture(), 0, buffer);
        float[] depths = new float[texelCount];
        buffer.get(depths);
        return depths;
    }

    private MeshRenderer driveAnimatedBar(EpysiaEngine engine, Scene scene, OpenGlRenderBackend backend,
                                          MeshRenderSystem meshes) {
        Skeleton skeleton = bendingSkeleton();
        MeshRenderer renderer = populate(scene, backend, skinnedBar(), skeleton);
        Camera3D camera = scene.componentsOf(Camera3D.class).getFirst();
        Animator animator = renderer.owner().orElseThrow().getComponent(Animator.class).orElseThrow();
        for (int frame = 0; frame < FRAMES; frame++) {
            animator.assignClip("memory:bend", bendClip(skeleton));
            engine.tick(input, 1.0f / 60.0f);
            engine.render(List.of(camera), RenderTargetHandle.SCREEN, 1.0f);
        }
        return renderer;
    }

    private void runCheck(Window window, OpenGlRenderBackend backend, EpysiaEngine engine) {
        Scene scene = new Scene("skinned deformation");
        MeshRenderSystem meshes = startEngine(window, backend, engine, scene);
        MeshData bar = skinnedBar();
        Skeleton skeleton = bendingSkeleton();
        MeshRenderer renderer = driveAnimatedBar(engine, scene, backend, meshes);
        BufferHandle deformedBuffer = meshes.deformedVertexBuffer(renderer).orElseThrow(() ->
                new IllegalStateException("no deformed vertex buffer, the check proves nothing"));
        float[] palette = readFloats(backend, paletteBufferOf(meshes, renderer),
                skeleton.jointCount() * MeshShaderBindings.JOINT_PALETTE_BYTES_PER_JOINT);
        float[] deformed = readFloats(backend, deformedBuffer,
                bar.vertexCount() * MeshShaderBindings.vertexStride(false, false));
        assertBent(bar, deformed);
        comparePositions(bar, palette, deformed);
    }

    private static BufferHandle paletteBufferOf(MeshRenderSystem meshes, MeshRenderer renderer) {
        return meshes.jointPaletteBinding(renderer).orElseThrow(() ->
                new IllegalStateException("no joint palette on the skinned renderer")).buffer();
    }

    private void comparePositions(MeshData bar, float[] palette, float[] deformed) {
        int stride = MeshShaderBindings.vertexStride(false, false) / Float.BYTES;
        float worst = 0.0f;
        for (int vertex = 0; vertex < bar.vertexCount(); vertex++) {
            float[] expected = skinOnCpu(bar, palette, vertex);
            for (int component = 0; component < 3; component++) {
                worst = Math.max(worst,
                        Math.abs(expected[component] - deformed[vertex * stride + component]));
            }
        }
        System.out.printf("skinned deformation check: %d vertices, worst position error %.7f%n",
                bar.vertexCount(), worst);
        assertTrue(worst < POSITION_TOLERANCE,
                "deformed vertices differ from palette skinning by " + worst);
    }

    private static float[] skinOnCpu(MeshData bar, float[] palette, int vertex) {
        float[] skinned = new float[3];
        for (int influence = 0; influence < MeshData.INFLUENCES_PER_VERTEX; influence++) {
            int slot = vertex * MeshData.INFLUENCES_PER_VERTEX + influence;
            float weight = bar.jointWeights()[slot];
            if (weight == 0.0f) {
                continue;
            }
            int row = bar.jointIndices()[slot] * 12;
            for (int component = 0; component < 3; component++) {
                int base = row + component * 4;
                skinned[component] += weight * (palette[base] * bar.positions()[vertex * 3]
                        + palette[base + 1] * bar.positions()[vertex * 3 + 1]
                        + palette[base + 2] * bar.positions()[vertex * 3 + 2]
                        + palette[base + 3]);
            }
        }
        return skinned;
    }

    private static void assertBent(MeshData bar, float[] deformed) {
        int stride = MeshShaderBindings.vertexStride(false, false) / Float.BYTES;
        float largestShift = 0.0f;
        for (int vertex = 0; vertex < bar.vertexCount(); vertex++) {
            largestShift = Math.max(largestShift,
                    Math.abs(deformed[vertex * stride] - bar.positions()[vertex * 3]));
        }
        assertTrue(largestShift > BEND_MINIMUM,
                "the animated pose never moved a vertex, the check proves nothing");
    }

    private static float[] readFloats(OpenGlRenderBackend backend, BufferHandle handle, int byteSize) {
        ByteBuffer bytes = BufferUtils.createByteBuffer(byteSize);
        backend.readBuffer(handle, bytes, 0L);
        float[] values = new float[byteSize / Float.BYTES];
        bytes.order(ByteOrder.nativeOrder()).asFloatBuffer().get(values);
        return values;
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

    private static MeshRenderer populate(Scene scene, OpenGlRenderBackend backend, MeshData bar,
                                         Skeleton skeleton) {
        GameObject cameraObject = new GameObject("Camera");
        cameraObject.addComponent(new Transform3D().setPosition(0.0f, 1.5f, 5.0f));
        cameraObject.addComponent(new Camera3D().setNearFar(0.1f, 60.0f));
        scene.addGameObject(cameraObject);
        GameObject sun = new GameObject("Sun");
        sun.addComponent(new Transform3D()).lookAt(-0.5f, -1.0f, -0.35f, 0.0f, 1.0f, 0.0f);
        sun.addComponent(new DirectionalLight().setIntensity(3.0f));
        scene.addGameObject(sun);
        GameObject barObject = new GameObject("Bar");
        barObject.addComponent(new Transform3D());
        MeshRenderer renderer = barObject.addComponent(new MeshRenderer())
                .setMesh(MeshUploader.upload(backend, bar, Optional.of(skeleton)))
                .setMaterial(new LitMaterial().setBaseColor(0.8f, 0.8f, 0.8f));
        barObject.addComponent(new Animator()).assignClip("memory:bend", bendClip(skeleton));
        scene.addGameObject(barObject);
        scene.advanceTick();
        return renderer;
    }

    private static Skeleton bendingSkeleton() {
        List<Joint> joints = new ArrayList<>(SEGMENTS);
        for (int index = 0; index < SEGMENTS; index++) {
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
                    new float[]{0.0f, 0.0f, 0.1305f, 0.9914f, 0.0f, 0.0f, 0.1305f, 0.9914f}));
        }
        return new Clip("bend", 1.0f, skeleton.nameChecksum(), channels);
    }

    private static MeshData skinnedBar() {
        int ringCount = SEGMENTS + 1;
        int vertexCount = ringCount * VERTICES_PER_RING;
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];
        float[] tangents = new float[vertexCount * 3];
        short[] jointIndices = new short[vertexCount * MeshData.INFLUENCES_PER_VERTEX];
        float[] jointWeights = new float[vertexCount * MeshData.INFLUENCES_PER_VERTEX];
        fillRings(ringCount, positions, normals, uvs, tangents, jointIndices, jointWeights);
        return new MeshData(positions, normals, uvs, tangents, jointIndices, jointWeights,
                ringIndices(ringCount), List.of());
    }

    private static void fillRings(int ringCount, float[] positions, float[] normals, float[] uvs,
                                  float[] tangents, short[] jointIndices, float[] jointWeights) {
        for (int ring = 0; ring < ringCount; ring++) {
            for (int corner = 0; corner < VERTICES_PER_RING; corner++) {
                int vertex = ring * VERTICES_PER_RING + corner;
                float side = (corner == 0 || corner == 3) ? -BAR_HALF_WIDTH : BAR_HALF_WIDTH;
                float depth = corner < 2 ? -BAR_HALF_WIDTH : BAR_HALF_WIDTH;
                positions[vertex * 3] = side;
                positions[vertex * 3 + 1] = ring * SEGMENT_HEIGHT;
                positions[vertex * 3 + 2] = depth;
                normals[vertex * 3] = side < 0.0f ? -1.0f : 1.0f;
                uvs[vertex * 2] = corner / (float) VERTICES_PER_RING;
                uvs[vertex * 2 + 1] = ring / (float) ringCount;
                tangents[vertex * 3 + 2] = 1.0f;
                jointIndices[vertex * MeshData.INFLUENCES_PER_VERTEX] = (short) Math.min(ring, SEGMENTS - 1);
                jointWeights[vertex * MeshData.INFLUENCES_PER_VERTEX] = 1.0f;
            }
        }
    }

    private static int[] ringIndices(int ringCount) {
        List<Integer> indices = new ArrayList<>();
        for (int ring = 0; ring + 1 < ringCount; ring++) {
            for (int corner = 0; corner < VERTICES_PER_RING; corner++) {
                int next = (corner + 1) % VERTICES_PER_RING;
                int low = ring * VERTICES_PER_RING;
                int high = (ring + 1) * VERTICES_PER_RING;
                indices.add(low + corner);
                indices.add(high + corner);
                indices.add(low + next);
                indices.add(low + next);
                indices.add(high + corner);
                indices.add(high + next);
            }
        }
        return indices.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean displayAvailable() {
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null;
    }
}
