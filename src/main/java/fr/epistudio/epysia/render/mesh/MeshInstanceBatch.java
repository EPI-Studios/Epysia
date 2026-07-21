package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferHandle;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

final class MeshInstanceBatch {

    private static final int INITIAL_CAPACITY = 8;

    private final Vector3f boundsMin = new Vector3f();
    private final Vector3f boundsMax = new Vector3f();
    private final Matrix4f firstModel = new Matrix4f();
    private final Matrix4f scratchNormal = new Matrix4f();

    private ByteBuffer instanceData = BufferUtils.createByteBuffer(
            INITIAL_CAPACITY * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
    private ByteBuffer culledData = BufferUtils.createByteBuffer(
            INITIAL_CAPACITY * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
    private int capacity = INITIAL_CAPACITY;
    private int culledCapacity = INITIAL_CAPACITY;
    private int instanceCount;
    private int visibleCount;
    private int culledCount;
    private long minDepthBits;
    private long transformHash;

    private UploadedSubmesh submesh;
    private PerSubmesh representative;
    private BufferHandle instanceBuffer;
    private BindingSetHandle litBindings;
    private BindingSetHandle shadowBindings;
    private int uploadedCapacity;
    private long boundLitBindingsId = -1L;
    private MaterialStateSnapshot state;

    void beginFrame() {
        boundsMin.set(Float.POSITIVE_INFINITY);
        boundsMax.set(Float.NEGATIVE_INFINITY);
        instanceData.clear();
        culledData.clear();
        instanceCount = 0;
        visibleCount = 0;
        culledCount = 0;
        minDepthBits = Long.MAX_VALUE;
        transformHash = ShadowSignatures.seed();
    }

    void accumulateBounds(Vector3f worldMin, Vector3f worldMax) {
        boundsMin.min(worldMin);
        boundsMax.max(worldMax);
    }

    Vector3f boundsMin() {
        return boundsMin;
    }

    Vector3f boundsMax() {
        return boundsMax;
    }

    void add(UploadedSubmesh addedSubmesh, PerSubmesh perSubmesh, Matrix4f model, long depthBits,
             boolean visible) {
        submesh = addedSubmesh;
        representative = perSubmesh;
        if (visibleCount + culledCount == 0) {
            firstModel.set(model);
        }
        if (visible) {
            ensureVisibleCapacity(visibleCount + 1);
            writeInstance(instanceData, visibleCount, model);
            visibleCount++;
            minDepthBits = Math.min(minDepthBits, depthBits);
        } else {
            ensureCulledCapacity(culledCount + 1);
            writeInstance(culledData, culledCount, model);
            culledCount++;
        }
        transformHash = ShadowSignatures.mixMatrix(transformHash, model);
    }

    void mergeCulledInstances() {
        instanceCount = visibleCount + culledCount;
        if (culledCount == 0) {
            return;
        }
        ensureVisibleCapacity(instanceCount);
        culledData.position(0).limit(culledCount * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        instanceData.position(visibleCount * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        instanceData.put(culledData);
        culledData.clear();
    }

    private void ensureVisibleCapacity(int required) {
        if (required <= capacity) {
            return;
        }
        int grown = capacity;
        while (grown < required) {
            grown *= 2;
        }
        ByteBuffer replacement = BufferUtils.createByteBuffer(grown * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        instanceData.position(0).limit(visibleCount * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        replacement.put(instanceData);
        instanceData = replacement;
        capacity = grown;
    }

    private void ensureCulledCapacity(int required) {
        if (required <= culledCapacity) {
            return;
        }
        int grown = culledCapacity;
        while (grown < required) {
            grown *= 2;
        }
        ByteBuffer replacement = BufferUtils.createByteBuffer(grown * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        culledData.position(0).limit(culledCount * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        replacement.put(culledData);
        culledData = replacement;
        culledCapacity = grown;
    }

    private void writeInstance(ByteBuffer target, int index, Matrix4f model) {
        int base = index * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;
        model.get(base, target);
        model.normal(scratchNormal);
        scratchNormal.get(base + 64, target);
    }

    ByteBuffer instancePayload() {
        instanceData.position(0);
        instanceData.limit(instanceCount * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        return instanceData;
    }

    long requiredByteSize() {
        return (long) capacity * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;
    }

    boolean needsBufferRebuild() {
        return instanceBuffer == null || uploadedCapacity != capacity
                || boundLitBindingsId != representative.litBindings().id();
    }

    void adoptResources(BufferHandle buffer, BindingSetHandle lit, BindingSetHandle shadow) {
        instanceBuffer = buffer;
        litBindings = lit;
        shadowBindings = shadow;
        uploadedCapacity = capacity;
        boundLitBindingsId = representative.litBindings().id();
    }

    void adoptState(MaterialStateSnapshot snapshot) {
        state = snapshot;
    }

    MaterialStateSnapshot state() {
        return state;
    }

    int instanceCount() {
        return instanceCount;
    }

    int visibleCount() {
        return visibleCount;
    }

    int pendingCount() {
        return visibleCount + culledCount;
    }

    long minDepthBits() {
        return minDepthBits;
    }

    long transformHash() {
        return transformHash;
    }

    Matrix4f firstModel() {
        return firstModel;
    }

    UploadedSubmesh submesh() {
        return submesh;
    }

    PerSubmesh representative() {
        return representative;
    }

    BufferHandle instanceBuffer() {
        return instanceBuffer;
    }

    BindingSetHandle litBindings() {
        return litBindings;
    }

    BindingSetHandle shadowBindings() {
        return shadowBindings;
    }
}
