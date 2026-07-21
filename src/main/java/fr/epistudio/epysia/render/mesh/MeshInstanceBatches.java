package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MeshInstanceBatches {

    @FunctionalInterface
    interface InstanceBindingSetFactory {
        BindingSetHandle create(PerSubmesh representative, BufferHandle instanceBuffer, long byteSize, boolean shadow);
    }

    private final Map<InstanceBatchKey, MeshInstanceBatch> batches = new HashMap<>();
    private final List<MeshInstanceBatch> activeBatches = new ArrayList<>();

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
                Matrix4f model, long depthBits, boolean visible,
                Vector3f worldMin, Vector3f worldMax) {
        InstanceBatchKey key = new InstanceBatchKey(submesh.handle().id(), state.digest());
        MeshInstanceBatch batch = batches.computeIfAbsent(key, ignored -> new MeshInstanceBatch());
        if (batch.pendingCount() == 0) {
            batch.beginFrame();
            batch.adoptState(state);
            activeBatches.add(batch);
        } else if (!batch.state().matches(state)) {
            return false;
        }
        batch.add(submesh, perSubmesh, model, depthBits, visible);
        batch.accumulateBounds(worldMin, worldMax);
        return true;
    }

    List<MeshInstanceBatch> activeBatches() {
        return activeBatches;
    }

    void upload(MeshInstanceBatch batch) {
        batch.mergeCulledInstances();
        if (batch.needsBufferRebuild()) {
            rebuildResources(batch);
        }
        backend.writeBuffer(batch.instanceBuffer(), batch.instancePayload(), 0L);
    }

    private void rebuildResources(MeshInstanceBatch batch) {
        releaseResources(batch);
        long byteSize = batch.requiredByteSize();
        BufferHandle buffer = backend.createBuffer(new BufferDescriptor(
                BufferUsage.STORAGE, BufferUtils.createByteBuffer((int) byteSize)));
        BindingSetHandle lit = bindingSetFactory.create(batch.representative(), buffer, byteSize, false);
        BindingSetHandle shadow = bindingSetFactory.create(batch.representative(), buffer, byteSize, true);
        batch.adoptResources(buffer, lit, shadow);
    }

    private void releaseResources(MeshInstanceBatch batch) {
        if (batch.instanceBuffer() == null) {
            return;
        }
        backend.destroy(batch.litBindings());
        backend.destroy(batch.shadowBindings());
        backend.destroy(batch.instanceBuffer());
    }

    void shutdown() {
        if (backend == null) {
            return;
        }
        for (MeshInstanceBatch batch : batches.values()) {
            releaseResources(batch);
        }
        batches.clear();
        activeBatches.clear();
    }
}
