package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.assets.epyprobes.BakedProbes;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.lighting.SphericalHarmonics;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Optional;

final class ProbeGridStorage {

    private RenderBackend backend;
    private BufferHandle handle;
    private long capacityBytes;
    private Optional<BakedProbes> uploaded = Optional.empty();

    void initialize(RenderBackend backend) {
        this.backend = backend;
        this.capacityBytes = (long) SphericalHarmonics.FLOAT_COUNT * Float.BYTES;
        this.handle = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer((int) capacityBytes)));
    }

    boolean update(Optional<BakedProbes> probes) {
        if (probes.isEmpty() || uploaded.filter(current -> current == probes.get()).isPresent()) {
            return false;
        }
        BakedProbes baked = probes.get();
        ByteBuffer data = packCoefficients(baked);
        uploaded = Optional.of(baked);
        if (data.remaining() <= capacityBytes) {
            backend.writeBuffer(handle, data, 0L);
            return false;
        }
        backend.destroy(handle);
        capacityBytes = data.remaining();
        handle = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, data));
        return true;
    }

    void writeProbe(int probeIndex, float[] coefficients) {
        long byteOffset = (long) probeIndex * SphericalHarmonics.FLOAT_COUNT * Float.BYTES;
        if (backend == null || handle == null || byteOffset + coefficients.length * Float.BYTES > capacityBytes) {
            return;
        }
        ByteBuffer data = BufferUtils.createByteBuffer(coefficients.length * Float.BYTES);
        for (float value : coefficients) {
            data.putFloat(value);
        }
        backend.writeBuffer(handle, data.flip(), byteOffset);
    }

    private static ByteBuffer packCoefficients(BakedProbes baked) {
        float[] coefficients = baked.coefficients();
        ByteBuffer data = BufferUtils.createByteBuffer(coefficients.length * Float.BYTES);
        for (float value : coefficients) {
            data.putFloat(value);
        }
        return data.flip();
    }

    BufferHandle handle() {
        return handle;
    }

    long byteSize() {
        return capacityBytes;
    }

    void shutdown() {
        if (backend != null && handle != null) {
            backend.destroy(handle);
            handle = null;
        }
        uploaded = Optional.empty();
    }
}
