package fr.epistudio.epysia.render.baking;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import fr.epistudio.epysia.assets.epyprobes.EpyProbesFormat;
import fr.epistudio.epysia.assets.epyprobes.EpyProbesWriter;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.LightProbeVolume;
import fr.epistudio.epysia.components.transforms.Transform3D;
import fr.epistudio.epysia.exceptions.EpysiaException;
import fr.epistudio.epysia.gameobjects.GameObject;
import fr.epistudio.epysia.render.RenderPasses;
import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BindingSetLayout;
import fr.epistudio.epysia.render.backend.BindingSlot;
import fr.epistudio.epysia.render.backend.BindingType;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.ComputeBarrier;
import fr.epistudio.epysia.render.backend.ComputeDispatch;
import fr.epistudio.epysia.render.backend.ComputePipelineDescriptor;
import fr.epistudio.epysia.render.backend.PassClear;
import fr.epistudio.epysia.render.backend.PipelineHandle;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.RenderTargetDescriptor;
import fr.epistudio.epysia.render.backend.RenderTargetHandle;
import fr.epistudio.epysia.render.backend.SampledTextureBinding;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.TextureDescriptor;
import fr.epistudio.epysia.render.backend.TextureFormat;
import fr.epistudio.epysia.render.backend.TextureHandle;
import fr.epistudio.epysia.render.backend.TextureUsage;
import fr.epistudio.epysia.render.lighting.CubeCaptureFace;
import fr.epistudio.epysia.render.lighting.SphericalHarmonics;
import fr.epistudio.epysia.scene.Scene;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class ProbeBaker implements LightBaker {

    private static final int FACE_SIZE = 64;
    private static final int SOURCE_FACE_BINDING = 0;
    private static final int FACE_TEXELS_BINDING = 1;
    private static final String COPY_COMPUTE_SOURCE = """
            #version 430 core
            layout(local_size_x = 8, local_size_y = 8) in;
            layout(binding = 0) uniform sampler2D sourceFace;
            layout(std430, binding = 1) writeonly buffer FaceTexels {
                vec4 texels[];
            };
            void main() {
                ivec2 texel = ivec2(gl_GlobalInvocationID.xy);
                texels[texel.y * 64 + texel.x] = texelFetch(sourceFace, texel, 0);
            }
            """;

    private Optional<BakeContext> context = Optional.empty();
    private Optional<Path> writtenFile = Optional.empty();

    @Override
    public LightBakeOutput output() {
        return LightBakeOutput.PROBES;
    }

    @Override
    public void start(BakeRequest request) {
        cancel();
        writtenFile = Optional.empty();
        context = Optional.of(new BakeContext(request));
    }

    @Override
    public BakeProgress step() {
        if (context.isEmpty()) {
            return BakeProgress.idle();
        }
        BakeContext running = context.get();
        running.bakeNextProbe();
        if (running.completed()) {
            writtenFile = Optional.of(running.finish());
            context = Optional.empty();
            return BakeProgress.done(running.probeCount());
        }
        return BakeProgress.running(running.completedProbes(), running.probeCount());
    }

    @Override
    public void cancel() {
        context.ifPresent(BakeContext::destroy);
        context = Optional.empty();
    }

    @Override
    public Optional<Path> result() {
        return writtenFile;
    }

    private static final class BakeContext {

        private final EpysiaEngine engine;
        private final RenderBackend backend;
        private final Scene scene;
        private final Path outputDirectory;
        private final Runnable stageBindingRestore;
        private final LightProbeVolume volume;
        private final Vector3f gridOrigin = new Vector3f();
        private final Vector3f gridSpacing = new Vector3f();
        private final float[] positions;
        private final float[] coefficients;
        private final float[][] faceRadiance = new float[CubeCaptureFace.COUNT][FACE_SIZE * FACE_SIZE * 3];
        private final ByteBuffer readback = BufferUtils.createByteBuffer(FACE_SIZE * FACE_SIZE * 4 * Float.BYTES);
        private final GameObject cameraObject;
        private final Transform3D cameraTransform;
        private final List<Camera3D> cameraList;
        private final Vector3f scratchForward = new Vector3f();
        private final Vector3f scratchUp = new Vector3f();
        private final Vector3f scratchPosition = new Vector3f();
        private final CaptureResources capture;
        private int probeIndex;
        private boolean destroyed;

        private BakeContext(BakeRequest request) {
            this.engine = request.engine();
            this.backend = engine.renderBackend();
            this.scene = engine.scene();
            this.outputDirectory = request.outputDirectory();
            this.stageBindingRestore = request.stageBindingRestore();
            this.volume = findVolume(scene);
            resolveGrid();
            this.positions = computePositions();
            this.coefficients = new float[positions.length / 3 * EpyProbesFormat.FLOATS_PER_PROBE];
            this.cameraObject = createCameraObject();
            this.cameraTransform = cameraObject.getComponent(Transform3D.class).orElseThrow();
            this.cameraList = List.of(cameraObject.getComponent(Camera3D.class).orElseThrow());
            this.capture = CaptureResources.create(backend);
        }

        private static LightProbeVolume findVolume(Scene scene) {
            for (GameObject gameObject : scene.gameObjects()) {
                LightProbeVolume candidate = gameObject.getComponentOrNull(LightProbeVolume.class);
                if (candidate != null && gameObject.getComponentOrNull(Transform3D.class) != null) {
                    return candidate;
                }
            }
            throw new EpysiaException("Probe bake requires a LightProbeVolume with a Transform3D in the scene.");
        }

        private void resolveGrid() {
            Transform3D transform = volume.owner().orElseThrow()
                    .getComponent(Transform3D.class).orElseThrow();
            Vector3f center = new Vector3f(transform.position());
            Vector3f extents = volume.extents(new Vector3f());
            gridOrigin.set(
                    axisOrigin(center.x, extents.x, volume.resolutionX()),
                    axisOrigin(center.y, extents.y, volume.resolutionY()),
                    axisOrigin(center.z, extents.z, volume.resolutionZ()));
            gridSpacing.set(
                    axisSpacing(extents.x, volume.resolutionX()),
                    axisSpacing(extents.y, volume.resolutionY()),
                    axisSpacing(extents.z, volume.resolutionZ()));
        }

        private static float axisOrigin(float center, float extent, int resolution) {
            return resolution > 1 ? center - extent : center;
        }

        private static float axisSpacing(float extent, int resolution) {
            return resolution > 1 ? 2.0f * extent / (resolution - 1) : 1.0f;
        }

        private float[] computePositions() {
            int count = volume.resolutionX() * volume.resolutionY() * volume.resolutionZ();
            float[] result = new float[count * 3];
            int cursor = 0;
            for (int z = 0; z < volume.resolutionZ(); z++) {
                for (int y = 0; y < volume.resolutionY(); y++) {
                    for (int x = 0; x < volume.resolutionX(); x++) {
                        result[cursor++] = gridOrigin.x + x * gridSpacing.x;
                        result[cursor++] = gridOrigin.y + y * gridSpacing.y;
                        result[cursor++] = gridOrigin.z + z * gridSpacing.z;
                    }
                }
            }
            return result;
        }

        private static GameObject createCameraObject() {
            GameObject cameraObject = new GameObject("probe-bake-camera");
            cameraObject.addComponent(new Transform3D());
            cameraObject.addComponent(new Camera3D()
                    .setActive(false)
                    .setFieldOfViewDegrees(90.0f)
                    .setAspectRatio(1.0f)
                    .setNearFar(0.05f, 500.0f));
            return cameraObject;
        }

        private void bakeNextProbe() {
            if (completed()) {
                return;
            }
            scratchPosition.set(positions[probeIndex * 3],
                    positions[probeIndex * 3 + 1], positions[probeIndex * 3 + 2]);
            bindCaptureStages();
            for (int face = 0; face < CubeCaptureFace.COUNT; face++) {
                captureFace(face);
            }
            float[] projected = SphericalHarmonics.project(faceRadiance, FACE_SIZE);
            System.arraycopy(projected, 0, coefficients,
                    probeIndex * EpyProbesFormat.FLOATS_PER_PROBE, EpyProbesFormat.FLOATS_PER_PROBE);
            probeIndex++;
            stageBindingRestore.run();
        }

        private void bindCaptureStages() {
            Vector3f clear = scene.clearColor();
            engine.bindStageTarget(RenderPasses.OPAQUE_3D, capture.faceTarget(),
                    PassClear.color(clear.x, clear.y, clear.z));
            engine.bindStageTarget(RenderPasses.TRANSPARENT_3D, capture.faceTarget(), PassClear.none());
        }

        private void captureFace(int face) {
            orientCamera(face);
            engine.render(cameraList, RenderTargetHandle.SCREEN, 1.0f);
            backend.dispatchCompute(new ComputeDispatch(capture.copyPipeline(), capture.copyBindings(),
                    FACE_SIZE / 8, FACE_SIZE / 8, 1));
            backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
            readback.clear();
            backend.readBuffer(capture.faceTexels(), readback, 0L);
            copyFaceRadiance(face);
        }

        private void orientCamera(int face) {
            CubeCaptureFace orientation = CubeCaptureFace.at(face);
            orientation.forward(scratchForward);
            orientation.up(scratchUp);
            cameraTransform.setPosition(scratchPosition.x, scratchPosition.y, scratchPosition.z);
            cameraTransform.lookAt(
                    scratchPosition.x + scratchForward.x,
                    scratchPosition.y + scratchForward.y,
                    scratchPosition.z + scratchForward.z,
                    scratchUp.x, scratchUp.y, scratchUp.z);
        }

        private void copyFaceRadiance(int face) {
            float[] destination = faceRadiance[face];
            for (int texel = 0; texel < FACE_SIZE * FACE_SIZE; texel++) {
                int source = texel * 4 * Float.BYTES;
                destination[texel * 3] = readback.getFloat(source);
                destination[texel * 3 + 1] = readback.getFloat(source + Float.BYTES);
                destination[texel * 3 + 2] = readback.getFloat(source + 2 * Float.BYTES);
            }
        }

        private boolean completed() {
            return probeIndex >= probeCount();
        }

        private int completedProbes() {
            return probeIndex;
        }

        private int probeCount() {
            return positions.length / 3;
        }

        private Path finish() {
            BakedProbes baked = new BakedProbes(LightBakeHashes.hashScene(scene), gridOrigin, gridSpacing,
                    volume.resolutionX(), volume.resolutionY(), volume.resolutionZ(), positions, coefficients);
            Path file = writeAsset(baked);
            volume.bakedProbesRef().setPath(file.toString());
            volume.bakedProbesRef().setDirect(baked);
            destroy();
            return file;
        }

        private Path writeAsset(BakedProbes baked) {
            try {
                Files.createDirectories(outputDirectory);
            } catch (IOException exception) {
                throw new EpysiaException("Failed to create probe output directory " + outputDirectory
                        + ": " + exception.getMessage(), exception);
            }
            Path file = outputDirectory.resolve(scene.name() + EpyProbesFormat.EXTENSION);
            EpyProbesWriter.writeToFile(file, baked);
            return file;
        }

        private void destroy() {
            if (destroyed) {
                return;
            }
            destroyed = true;
            capture.destroy(backend);
            stageBindingRestore.run();
        }
    }

    private record CaptureResources(TextureHandle faceColor, TextureHandle faceDepth,
                                    RenderTargetHandle faceTarget, PipelineHandle copyPipeline,
                                    BufferHandle faceTexels, BindingSetHandle copyBindings) {

        private static final long FACE_BYTES = (long) FACE_SIZE * FACE_SIZE * 4 * Float.BYTES;

        private static CaptureResources create(RenderBackend backend) {
            TextureHandle faceColor = backend.createTexture(new TextureDescriptor(
                    FACE_SIZE, FACE_SIZE, TextureFormat.RGBA16F, TextureUsage.SAMPLED));
            TextureHandle faceDepth = backend.createTexture(new TextureDescriptor(
                    FACE_SIZE, FACE_SIZE, TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT));
            RenderTargetHandle faceTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                    FACE_SIZE, FACE_SIZE, List.of(faceColor), Optional.of(faceDepth)));
            return withComputeResources(backend, faceColor, faceDepth, faceTarget);
        }

        private static CaptureResources withComputeResources(RenderBackend backend, TextureHandle faceColor,
                                                             TextureHandle faceDepth, RenderTargetHandle faceTarget) {
            BindingSetLayout copyLayout = new BindingSetLayout(List.of(
                    new BindingSlot(SOURCE_FACE_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                    new BindingSlot(FACE_TEXELS_BINDING, BindingType.STORAGE_BUFFER)));
            PipelineHandle copyPipeline = backend.createComputePipeline(
                    new ComputePipelineDescriptor(COPY_COMPUTE_SOURCE, copyLayout));
            BufferHandle faceTexels = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                    BufferUtils.createByteBuffer((int) FACE_BYTES)));
            BindingSetHandle copyBindings = backend.createBindingSet(new BindingSetDescriptor(copyLayout, List.of(
                    new Binding(SOURCE_FACE_BINDING, new SampledTextureBinding(faceColor)),
                    new Binding(FACE_TEXELS_BINDING, StorageBufferBinding.whole(faceTexels, FACE_BYTES)))));
            return new CaptureResources(faceColor, faceDepth, faceTarget, copyPipeline, faceTexels, copyBindings);
        }

        private void destroy(RenderBackend backend) {
            backend.destroy(copyBindings);
            backend.destroy(faceTexels);
            backend.destroy(copyPipeline);
            backend.destroy(faceTarget);
            backend.destroy(faceDepth);
            backend.destroy(faceColor);
        }
    }
}
