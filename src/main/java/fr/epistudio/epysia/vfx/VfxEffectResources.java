package fr.epistudio.epysia.vfx;

import fr.epistudio.epysia.render.backend.Binding;
import fr.epistudio.epysia.render.backend.BindingSetDescriptor;
import fr.epistudio.epysia.render.backend.BindingSetHandle;
import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import fr.epistudio.epysia.render.backend.StorageBufferBinding;
import fr.epistudio.epysia.render.backend.UniformBufferBinding;
import fr.epistudio.epysia.render.mesh.MeshShaderBindings;
import fr.epistudio.epysia.vfx.lut.VfxLutPack;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.List;

final class VfxEffectResources {

    static final int PARTICLE_BYTES = 96;
    static final int EFFECT_UBO_BYTES = 80;
    static final int INDIRECT_BYTES = 20;
    static final int POOL_BINDING = 5;
    static final int ALIVE_BINDING = 6;
    static final int FREE_BINDING = 7;
    static final int INDIRECT_BINDING = 8;
    static final int CURVE_LUT_BINDING = 9;
    static final int GRADIENT_LUT_BINDING = 10;

    private static final int MINIMUM_CURVE_FLOATS = VfxLutPack.VFX_LUT_RESOLUTION;
    private static final int MINIMUM_GRADIENT_FLOATS = VfxLutPack.VFX_LUT_RESOLUTION * 4;

    private final RenderBackend backend;
    private final VfxBindingLayouts layouts;
    private final int poolSize;
    private final BufferHandle pool;
    private final BufferHandle aliveList;
    private final BufferHandle freeList;
    private final BufferHandle indirectBuffer;
    private final BufferHandle effectUbo;

    private BufferHandle curveLut;
    private BufferHandle gradientLut;
    private long curveLutBytes;
    private long gradientLutBytes;
    private BindingSetHandle computeBindings;
    private BindingSetHandle drawBindings;
    private VfxLutPack uploadedPack;

    VfxEffectResources(RenderBackend backend, VfxBindingLayouts layouts, int poolSize) {
        this.backend = backend;
        this.layouts = layouts;
        this.poolSize = poolSize;
        this.pool = storageBuffer(poolSize * PARTICLE_BYTES);
        this.aliveList = storageBuffer(poolSize * Integer.BYTES);
        this.freeList = backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE, initialFreeList(poolSize)));
        this.indirectBuffer = backend.createBuffer(new BufferDescriptor(BufferUsage.INDIRECT,
                BufferUtils.createByteBuffer(INDIRECT_BYTES)));
        this.effectUbo = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM,
                BufferUtils.createByteBuffer(EFFECT_UBO_BYTES)));
        allocateLutBuffers(MINIMUM_CURVE_FLOATS * (long) Float.BYTES, MINIMUM_GRADIENT_FLOATS * (long) Float.BYTES);
        createBindingSets();
    }

    private BufferHandle storageBuffer(int byteSize) {
        return backend.createBuffer(new BufferDescriptor(BufferUsage.STORAGE,
                BufferUtils.createByteBuffer(byteSize)));
    }

    private static ByteBuffer initialFreeList(int poolSize) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(Integer.BYTES + poolSize * Integer.BYTES);
        buffer.putInt(poolSize);
        for (int slot = 0; slot < poolSize; slot++) {
            buffer.putInt(slot);
        }
        buffer.flip();
        return buffer;
    }

    private void allocateLutBuffers(long curveBytes, long gradientBytes) {
        curveLutBytes = curveBytes;
        gradientLutBytes = gradientBytes;
        curveLut = storageBuffer((int) curveBytes);
        gradientLut = storageBuffer((int) gradientBytes);
    }

    private void createBindingSets() {
        computeBindings = backend.createBindingSet(new BindingSetDescriptor(layouts.computeLayout(), List.of(
                new Binding(POOL_BINDING, StorageBufferBinding.whole(pool, (long) poolSize * PARTICLE_BYTES)),
                new Binding(ALIVE_BINDING, StorageBufferBinding.whole(aliveList, (long) poolSize * Integer.BYTES)),
                new Binding(FREE_BINDING, StorageBufferBinding.whole(freeList, freeListBytes())),
                new Binding(INDIRECT_BINDING, StorageBufferBinding.whole(indirectBuffer, INDIRECT_BYTES)),
                new Binding(CURVE_LUT_BINDING, StorageBufferBinding.whole(curveLut, curveLutBytes)),
                new Binding(GRADIENT_LUT_BINDING, StorageBufferBinding.whole(gradientLut, gradientLutBytes)),
                new Binding(1, UniformBufferBinding.whole(effectUbo, EFFECT_UBO_BYTES)))));
        drawBindings = backend.createBindingSet(new BindingSetDescriptor(layouts.drawLayout(), List.of(
                new Binding(0, UniformBufferBinding.whole(layouts.frameUniformBuffer(),
                        MeshShaderBindings.FRAME_UBO_SIZE)),
                new Binding(1, UniformBufferBinding.whole(effectUbo, EFFECT_UBO_BYTES)),
                new Binding(POOL_BINDING, StorageBufferBinding.whole(pool, (long) poolSize * PARTICLE_BYTES)),
                new Binding(ALIVE_BINDING, StorageBufferBinding.whole(aliveList, (long) poolSize * Integer.BYTES)),
                new Binding(FREE_BINDING, StorageBufferBinding.whole(freeList, freeListBytes())),
                new Binding(INDIRECT_BINDING, StorageBufferBinding.whole(indirectBuffer, INDIRECT_BYTES)),
                new Binding(CURVE_LUT_BINDING, StorageBufferBinding.whole(curveLut, curveLutBytes)),
                new Binding(GRADIENT_LUT_BINDING, StorageBufferBinding.whole(gradientLut, gradientLutBytes)))));
    }

    private long freeListBytes() {
        return Integer.BYTES + (long) poolSize * Integer.BYTES;
    }

    void uploadLut(VfxLutPack pack) {
        if (uploadedPack == pack) {
            return;
        }
        uploadedPack = pack;
        long neededCurveBytes = lutBytes(pack.curveSamples().length, MINIMUM_CURVE_FLOATS);
        long neededGradientBytes = lutBytes(pack.gradientSamples().length, MINIMUM_GRADIENT_FLOATS);
        if (neededCurveBytes > curveLutBytes || neededGradientBytes > gradientLutBytes) {
            resizeLutBuffers(Math.max(neededCurveBytes, curveLutBytes),
                    Math.max(neededGradientBytes, gradientLutBytes));
        }
        writeFloats(curveLut, pack.curveSamples());
        writeFloats(gradientLut, pack.gradientSamples());
    }

    private static long lutBytes(int floatCount, int minimumFloats) {
        return (long) Math.max(floatCount, minimumFloats) * Float.BYTES;
    }

    private void resizeLutBuffers(long curveBytes, long gradientBytes) {
        backend.destroy(computeBindings);
        backend.destroy(drawBindings);
        backend.destroy(curveLut);
        backend.destroy(gradientLut);
        allocateLutBuffers(curveBytes, gradientBytes);
        createBindingSets();
    }

    private void writeFloats(BufferHandle buffer, float[] samples) {
        if (samples.length == 0) {
            return;
        }
        ByteBuffer bytes = BufferUtils.createByteBuffer(samples.length * Float.BYTES);
        bytes.asFloatBuffer().put(samples);
        backend.writeBuffer(buffer, bytes, 0L);
    }

    void writeEffectUbo(ByteBuffer contents) {
        backend.writeBuffer(effectUbo, contents, 0L);
    }

    void destroy() {
        backend.destroy(computeBindings);
        backend.destroy(drawBindings);
        backend.destroy(pool);
        backend.destroy(aliveList);
        backend.destroy(freeList);
        backend.destroy(indirectBuffer);
        backend.destroy(effectUbo);
        backend.destroy(curveLut);
        backend.destroy(gradientLut);
    }

    BufferHandle pool() {
        return pool;
    }

    BufferHandle freeList() {
        return freeList;
    }

    BufferHandle indirectBuffer() {
        return indirectBuffer;
    }

    BindingSetHandle computeBindings() {
        return computeBindings;
    }

    BindingSetHandle drawBindings() {
        return drawBindings;
    }

    int poolSize() {
        return poolSize;
    }
}
