package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferHandle;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

final class MeshInstanceBatch {
    private static final int INITIAL_CAPACITY = 8;
    private static final int INSTANCE_FLOAT_COUNT = MeshShaderBindings.INSTANCE_TRANSFORM_BYTES / Float.BYTES;

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
    private long uploadHash;
    private int slotsPending;
    private long lastUploadedHash;

    private static final BindingSetHandle[] NO_BINDINGS = new BindingSetHandle[0];
    private static final int[] NO_TILES = new int[0];
    private static final float[] NO_BOUNDS = new float[0];

    private UploadedSubmesh submesh;
    private PerSubmesh representative;
    private BufferHandle instanceBuffer;
    private BindingSetHandle[] litBindings = NO_BINDINGS;
    private BindingSetHandle[] shadowBindings = NO_BINDINGS;
    private int[] tileStart = NO_TILES;
    private int[] tileLength = NO_TILES;
    private float[] tileBounds = NO_BOUNDS;
    private int tileCount = 1;
    private int uploadedTileCount = 1;
    private int uploadedCapacity;
    private long boundLitBindingsId = -1L;
    private MaterialStateSnapshot state;
    private boolean bulk;
    private boolean castsShadows = true;
    private boolean vertexColored;
    private float visibilityRangeBegin;
    private float visibilityRangeEnd;
    private long bulkRevision = -1L;

    void beginFrame() {
        bulk = false;
        castsShadows = true;
        vertexColored = false;
        visibilityRangeBegin = 0.0f;
        visibilityRangeEnd = 0.0f;
        tileStart = NO_TILES;
        tileLength = NO_TILES;
        tileBounds = NO_BOUNDS;
        tileCount = 1;
        boundsMin.set(Float.POSITIVE_INFINITY);
        boundsMax.set(Float.NEGATIVE_INFINITY);
        instanceData.clear();
        culledData.clear();
        instanceCount = 0;
        visibleCount = 0;
        culledCount = 0;
        minDepthBits = Long.MAX_VALUE;
        transformHash = ShadowSignatures.seed();
        uploadHash = ShadowSignatures.seed();
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
             boolean visible, int layer) {
        submesh = addedSubmesh;
        representative = perSubmesh;
        if (visibleCount + culledCount == 0) {
            firstModel.set(model);
        }
        if (visible) {
            ensureVisibleCapacity(visibleCount + 1);
            writeInstance(instanceData, visibleCount, model, layer);
            visibleCount++;
            minDepthBits = Math.min(minDepthBits, depthBits);
        } else {
            ensureCulledCapacity(culledCount + 1);
            writeInstance(culledData, culledCount, model, layer);
            culledCount++;
        }
        transformHash = ShadowSignatures.mixMatrix(transformHash, model);
        uploadHash = ShadowSignatures.mix(ShadowSignatures.mixMatrix(uploadHash, model), visible ? 1L : 0L);
    }

    void beginBulk(UploadedSubmesh addedSubmesh, PerSubmesh perSubmesh) {
        submesh = addedSubmesh;
        representative = perSubmesh;
        bulk = true;
    }

    void setCastsShadows(boolean value) {
        castsShadows = value;
    }

    void setVertexColored(boolean value) {
        vertexColored = value;
    }

    boolean vertexColored() {
        return vertexColored;
    }

    void setVisibilityRange(float begin, float end) {
        visibilityRangeBegin = begin;
        visibilityRangeEnd = end;
    }

    float visibilityRangeBegin() {
        return visibilityRangeBegin;
    }

    float visibilityRangeEnd() {
        return visibilityRangeEnd;
    }

    boolean castsShadows() {
        return castsShadows;
    }

    void writeBulkIfStale(float[] source, int count, long revision) {
        if (bulkRevision == revision && capacity == count) {
            return;
        }
        int byteSize = count * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;
        if (instanceData.capacity() < byteSize) {
            instanceData = BufferUtils.createByteBuffer(byteSize);
        }
        instanceData.clear();
        instanceData.asFloatBuffer().put(source, 0, count * INSTANCE_FLOAT_COUNT);
        firstModel.set(source);
        capacity = count;
        bulkRevision = revision;
    }

    int tileDrawCount(int tile) {
        if (tileLength.length == 0) {
            return visibleCount;
        }
        return Math.max(0, Math.min(tileLength[tile], instanceCount - tileStart[tile]));
    }

    void adoptBulkCounts(int count, boolean visible, long depthBits) {
        instanceCount = count;
        visibleCount = visible ? count : 0;
        culledCount = visible ? 0 : count;
        minDepthBits = depthBits;
        transformHash = bulkRevision;
        uploadHash = ShadowSignatures.mix(bulkRevision, count);
    }

    boolean uploadUnchangedSinceLastFrame() {
        return uploadHash == lastUploadedHash && slotsPending <= 0;
    }

    void markUploaded(int ringSlots) {
        slotsPending = uploadHash == lastUploadedHash ? slotsPending - 1 : ringSlots - 1;
        lastUploadedHash = uploadHash;
    }

    void invalidateUpload() {
        lastUploadedHash = 0L;
    }

    void mergeCulledInstances() {
        if (bulk) {
            return;
        }
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

    private void writeInstance(ByteBuffer target, int index, Matrix4f model, int layer) {
        int base = index * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;
        model.get(base, target);
        model.normal(scratchNormal);
        scratchNormal.get(base + 64, target);
        target.putFloat(base + MeshShaderBindings.INSTANCE_LAYER_BYTE_OFFSET, layer);
    }

    ByteBuffer instancePayload() {
        int uploaded = bulk ? capacity : instanceCount;
        instanceData.position(0);
        instanceData.limit(uploaded * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES);
        return instanceData;
    }

    long requiredByteSize() {
        return (long) capacity * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;
    }

    boolean needsBufferRebuild() {
        return instanceBuffer == null || uploadedCapacity != capacity || uploadedTileCount != tileCount;
    }

    boolean needsBindingRebuild() {
        return representative == null || boundLitBindingsId != representative.litBindings().id();
    }

    boolean represents(PerSubmesh candidate) {
        return representative == candidate;
    }

    void forgetRepresentative() {
        representative = null;
        boundLitBindingsId = -1L;
        litBindings = NO_BINDINGS;
        shadowBindings = NO_BINDINGS;
        visibleCount = 0;
        culledCount = 0;
    }

    void adoptResources(BufferHandle buffer, BindingSetHandle[] lit, BindingSetHandle[] shadow) {
        instanceBuffer = buffer;
        uploadedCapacity = capacity;
        uploadedTileCount = tileCount;
        adoptBindings(lit, shadow);
    }

    void adoptBindings(BindingSetHandle[] lit, BindingSetHandle[] shadow) {
        litBindings = lit;
        shadowBindings = shadow;
        boundLitBindingsId = representative.litBindings().id();
    }

    void adoptTiles(int[] starts, int[] lengths, float[] bounds) {
        tileStart = starts;
        tileLength = lengths;
        tileBounds = bounds;
        tileCount = Math.max(1, starts.length);
    }

    void tileBounds(int tile, Vector3f outMin, Vector3f outMax) {
        if (tileBounds.length < (tile + 1) * 6) {
            outMin.set(boundsMin);
            outMax.set(boundsMax);
            return;
        }
        int base = tile * 6;
        outMin.set(tileBounds[base], tileBounds[base + 1], tileBounds[base + 2]);
        outMax.set(tileBounds[base + 3], tileBounds[base + 4], tileBounds[base + 5]);
    }

    int tileCount() {
        return tileCount;
    }

    int tileInstanceStart(int tile) {
        return tileStart.length == 0 ? 0 : tileStart[tile];
    }

    int tileInstanceCount(int tile) {
        return tileLength.length == 0 ? instanceCount : tileLength[tile];
    }

    long tileByteOffset(int tile) {
        return (long) tileInstanceStart(tile) * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;
    }

    long tileByteSize(int tile) {
        return (long) Math.max(1, tileInstanceCount(tile)) * MeshShaderBindings.INSTANCE_TRANSFORM_BYTES;
    }

    BindingSetHandle[] allLitBindings() {
        return litBindings;
    }

    BindingSetHandle[] allShadowBindings() {
        return shadowBindings;
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

    private Aabb localBounds;

    void setLocalBounds(Aabb bounds) {
        this.localBounds = bounds;
    }

    Aabb localBounds() {
        return localBounds;
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

    BindingSetHandle litBindings(int tile) {
        return litBindings[tile];
    }

    BindingSetHandle shadowBindings(int tile) {
        return shadowBindings[tile];
    }
}
