package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.components.MultiMeshRenderer;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MeshInstanceBatches {

    @FunctionalInterface
    interface InstanceBindingSetFactory {
        BindingSetHandle create(PerSubmesh representative, BufferHandle instanceBuffer, long byteOffset,
                                long byteSize, boolean shadow);
    }

    private final LongLongHashMap batchIndices = new LongLongHashMap(64);
    private final List<MeshInstanceBatch> batches = new ArrayList<>();
    private final List<MeshInstanceBatch> activeBatches = new ArrayList<>();
    private final Map<MultiMeshRenderer, BulkInstances> bulkInstances = new IdentityHashMap<>();

    private RenderBackend backend;
    private InstanceBindingSetFactory bindingSetFactory;

    void initialize(RenderBackend renderBackend, InstanceBindingSetFactory factory) {
        this.backend = renderBackend;
        this.bindingSetFactory = factory;
    }

    void beginFrame() {
        for (MeshInstanceBatch batch : activeBatches) {
            batch.beginFrame();
        }
        activeBatches.clear();
    }

    boolean add(UploadedSubmesh submesh, PerSubmesh perSubmesh, MaterialStateSnapshot state,
                Matrix4f model, long depthBits, boolean visible, boolean castsShadows,
                Vector3f worldMin, Vector3f worldMax, Aabb localBounds) {
        MeshInstanceBatch batch = batchFor(submesh.handle().id(), state.digest(), castsShadows);
        if (batch.pendingCount() == 0) {
            batch.beginFrame();
            batch.adoptState(state);
            batch.setCastsShadows(castsShadows);
            batch.setLocalBounds(localBounds);
            activeBatches.add(batch);
        } else if (!batch.state().matches(state)) {
            return false;
        }
        batch.add(submesh, perSubmesh, model, depthBits, visible);
        batch.accumulateBounds(worldMin, worldMax);
        return true;
    }

    private MeshInstanceBatch batchFor(long submeshId, long materialDigest, boolean castsShadows) {
        long key = submeshId * 0x9E3779B97F4A7C15L ^ materialDigest ^ (castsShadows ? 0L : 0x5BF03635L);
        long index = batchIndices.getOrDefault(key, -1L);
        if (index >= 0L) {
            return batches.get((int) index);
        }
        MeshInstanceBatch batch = new MeshInstanceBatch();
        batchIndices.put(key, batches.size());
        batches.add(batch);
        return batch;
    }

    List<MeshInstanceBatch> activeBatches() {
        return activeBatches;
    }

    BulkInstances bulkFor(MultiMeshRenderer renderer, int submeshCount) {
        BulkInstances existing = bulkInstances.get(renderer);
        if (existing != null && existing.batchCount() == submeshCount) {
            return existing;
        }
        releaseBulk(renderer);
        BulkInstances created = new BulkInstances(submeshCount);
        bulkInstances.put(renderer, created);
        return created;
    }

    Optional<BufferHandle> instanceBufferFor(MultiMeshRenderer renderer) {
        BulkInstances bulk = bulkInstances.get(renderer);
        if (bulk == null || bulk.batchCount() == 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(bulk.batch(0).instanceBuffer());
    }

    void activate(MeshInstanceBatch batch) {
        activeBatches.add(batch);
    }

    void releaseBulk(MultiMeshRenderer renderer) {
        BulkInstances removed = bulkInstances.remove(renderer);
        if (removed == null) {
            return;
        }
        for (MeshInstanceBatch batch : removed.batches()) {
            releaseResources(batch);
        }
    }

    void upload(MeshInstanceBatch batch) {
        batch.mergeCulledInstances();
        if (batch.needsBufferRebuild()) {
            rebuildResources(batch);
            batch.invalidateUpload();
        }
        if (batch.uploadUnchangedSinceLastFrame()) {
            return;
        }
        backend.writeBuffer(batch.instanceBuffer(), batch.instancePayload(), 0L);
        batch.markUploaded();
    }

    private void rebuildResources(MeshInstanceBatch batch) {
        releaseResources(batch);
        long byteSize = batch.requiredByteSize();
        BufferHandle buffer = backend.createBuffer(new BufferDescriptor(
                BufferUsage.STORAGE, BufferUtils.createByteBuffer((int) byteSize)));
        int tiles = batch.tileCount();
        BindingSetHandle[] lit = new BindingSetHandle[tiles];
        BindingSetHandle[] shadow = new BindingSetHandle[tiles];
        for (int tile = 0; tile < tiles; tile++) {
            long offset = batch.tileByteOffset(tile);
            long size = batch.tileByteSize(tile);
            lit[tile] = bindingSetFactory.create(batch.representative(), buffer, offset, size, false);
            shadow[tile] = bindingSetFactory.create(batch.representative(), buffer, offset, size, true);
        }
        batch.adoptResources(buffer, lit, shadow);
    }

    private void releaseResources(MeshInstanceBatch batch) {
        if (batch.instanceBuffer() == null) {
            return;
        }
        for (BindingSetHandle handle : batch.allLitBindings()) {
            backend.destroy(handle);
        }
        for (BindingSetHandle handle : batch.allShadowBindings()) {
            backend.destroy(handle);
        }
        backend.destroy(batch.instanceBuffer());
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        for (MeshInstanceBatch batch : batches) {
            releaseResources(batch);
        }
        for (BulkInstances bulk : bulkInstances.values()) {
            for (MeshInstanceBatch batch : bulk.batches()) {
                releaseResources(batch);
            }
        }
        bulkInstances.clear();
        batches.clear();
        batchIndices.clear();
        activeBatches.clear();
    }

    static final class BulkInstances {

        private final List<MeshInstanceBatch> batches;
        private final InstanceTiles tiles = new InstanceTiles();
        private final Vector3f boundsMin = new Vector3f();
        private final Vector3f boundsMax = new Vector3f();
        private long revision = -1L;
        private int instanceCount = -1;

        private BulkInstances(int submeshCount) {
            batches = new ArrayList<>(submeshCount);
            for (int index = 0; index < submeshCount; index++) {
                batches.add(new MeshInstanceBatch());
            }
        }

        List<MeshInstanceBatch> batches() {
            return batches;
        }

        int batchCount() {
            return batches.size();
        }

        MeshInstanceBatch batch(int index) {
            return batches.get(index);
        }

        boolean tilesMatch(long candidateRevision, int candidateCount) {
            return revision == candidateRevision && instanceCount == candidateCount;
        }

        void rebuildTiles(float[] source, int count, long candidateRevision, Aabb localBounds) {
            revision = candidateRevision;
            instanceCount = count;
            boundsMin.set(Float.POSITIVE_INFINITY);
            boundsMax.set(Float.NEGATIVE_INFINITY);
            tiles.build(source, count, localBounds, boundsMin, boundsMax);
        }

        InstanceTiles tiles() {
            return tiles;
        }

        Vector3f boundsMin() {
            return boundsMin;
        }

        Vector3f boundsMax() {
            return boundsMax;
        }
    }
}
