package fr.epistudio.epysia.render.lighting;

import fr.epistudio.epysia.EpysiaEngine;
import fr.epistudio.epysia.components.Camera3D;
import fr.epistudio.epysia.components.transforms.Transform3D;
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
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

public final class ProbeRadianceCapture {

    private static final int SOURCE_FACE_BINDING = 0;
    private static final int FACE_TEXELS_BINDING = 1;
    private static final int WORKGROUP_SIZE = 8;

    private final int faceSize;
    private final RenderBackend backend;
    private final ByteBuffer readback;
    private final GameObject cameraObject;
    private final Transform3D cameraTransform;
    private final List<Camera3D> cameraList;
    private final Vector3f scratchForward = new Vector3f();
    private final Vector3f scratchUp = new Vector3f();
    private final TextureHandle faceColor;
    private final TextureHandle faceDepth;
    private final RenderTargetHandle faceTarget;
    private final PipelineHandle copyPipeline;
    private final BufferHandle faceTexels;
    private final BindingSetHandle copyBindings;
    private boolean destroyed;

    public ProbeRadianceCapture(RenderBackend backend, int faceSize) {
        this.backend = backend;
        this.faceSize = faceSize;
        this.readback = BufferUtils.createByteBuffer(faceSize * faceSize * 4 * Float.BYTES);
        this.cameraObject = createCameraObject();
        this.cameraTransform = cameraObject.getComponent(Transform3D.class).orElseThrow();
        this.cameraList = List.of(cameraObject.getComponent(Camera3D.class).orElseThrow());
        this.faceColor = backend.createTexture(new TextureDescriptor(
                faceSize, faceSize, TextureFormat.RGBA16F, TextureUsage.SAMPLED));
        this.faceDepth = backend.createTexture(new TextureDescriptor(
                faceSize, faceSize, TextureFormat.DEPTH32F, TextureUsage.SAMPLED_DEPTH_ATTACHMENT));
        this.faceTarget = backend.createRenderTarget(new RenderTargetDescriptor(
                faceSize, faceSize, List.of(faceColor), Optional.of(faceDepth)));
        BindingSetLayout copyLayout = new BindingSetLayout(List.of(
                new BindingSlot(SOURCE_FACE_BINDING, BindingType.SAMPLED_TEXTURE_2D),
                new BindingSlot(FACE_TEXELS_BINDING, BindingType.STORAGE_BUFFER)));
        this.copyPipeline = backend.createComputePipeline(
                new ComputePipelineDescriptor(ProbeFaceCopyShader.source(faceSize, WORKGROUP_SIZE), copyLayout));
        this.faceTexels = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(faceByteCount())));
        this.copyBindings = backend.createBindingSet(new BindingSetDescriptor(copyLayout, List.of(
                new Binding(SOURCE_FACE_BINDING, new SampledTextureBinding(faceColor)),
                new Binding(FACE_TEXELS_BINDING, StorageBufferBinding.whole(faceTexels, faceByteCount())))));
    }

    private int faceByteCount() {
        return faceSize * faceSize * 4 * Float.BYTES;
    }

    public int faceSize() {
        return faceSize;
    }

    public int radianceFloatCount() {
        return faceSize * faceSize * 3;
    }

    private static GameObject createCameraObject() {
        GameObject cameraObject = new GameObject("probe-capture-camera");
        cameraObject.addComponent(new Transform3D());
        cameraObject.addComponent(new Camera3D()
                .setActive(false)
                .setFieldOfViewDegrees(90.0f)
                .setAspectRatio(1.0f)
                .setNearFar(0.05f, 500.0f));
        return cameraObject;
    }

    public void bindStages(EpysiaEngine engine, Vector3f clearColor) {
        engine.bindStageTarget(RenderPasses.OPAQUE_3D, faceTarget,
                PassClear.color(clearColor.x, clearColor.y, clearColor.z));
        engine.bindStageTarget(RenderPasses.TRANSPARENT_3D, faceTarget, PassClear.none());
    }

    public void captureFace(EpysiaEngine engine, Vector3f position, int face, float[] destination) {
        orientCamera(position, face);
        engine.render(cameraList, RenderTargetHandle.SCREEN, 1.0f);
        backend.dispatchCompute(new ComputeDispatch(copyPipeline, copyBindings,
                faceSize / WORKGROUP_SIZE, faceSize / WORKGROUP_SIZE, 1));
        backend.computeBarrier(ComputeBarrier.STORAGE_BUFFER);
        readback.clear();
        backend.readBuffer(faceTexels, readback, 0L);
        copyRadiance(destination);
    }

    private void orientCamera(Vector3f position, int face) {
        CubeCaptureFace orientation = CubeCaptureFace.at(face);
        orientation.forward(scratchForward);
        orientation.up(scratchUp);
        cameraTransform.setPosition(position.x, position.y, position.z);
        cameraTransform.lookAt(
                position.x + scratchForward.x,
                position.y + scratchForward.y,
                position.z + scratchForward.z,
                scratchUp.x, scratchUp.y, scratchUp.z);
    }

    private void copyRadiance(float[] destination) {
        for (int texel = 0; texel < faceSize * faceSize; texel++) {
            int source = texel * 4 * Float.BYTES;
            destination[texel * 3] = readback.getFloat(source);
            destination[texel * 3 + 1] = readback.getFloat(source + Float.BYTES);
            destination[texel * 3 + 2] = readback.getFloat(source + 2 * Float.BYTES);
        }
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        backend.destroy(copyBindings);
        backend.destroy(faceTexels);
        backend.destroy(copyPipeline);
        backend.destroy(faceTarget);
        backend.destroy(faceDepth);
        backend.destroy(faceColor);
    }
}
