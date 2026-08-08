package fr.epistudio.epysia.render.sprite;

import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.joml.Matrix3x2f;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public final class Object2dUniform {
    public static final int BINDING = 5;
    public static final int BYTE_SIZE = 32;

    private final Matrix3x2f written = new Matrix3x2f();
    private final ByteBuffer staging = BufferUtils.createByteBuffer(BYTE_SIZE);
    private final BufferHandle handle;

    public Object2dUniform(RenderBackend backend) {
        this.handle = backend.createBuffer(new BufferDescriptor(BufferUsage.UNIFORM, fill(new Matrix3x2f())));
    }

    public BufferHandle handle() {
        return handle;
    }

    public void write(RenderBackend backend, Matrix3x2f matrix) {
        if (written.equals(matrix)) {
            return;
        }
        written.set(matrix);
        backend.writeBuffer(handle, fill(matrix), 0L);
    }

    public void destroy(RenderBackend backend) {
        backend.destroy(handle);
    }

    private ByteBuffer fill(Matrix3x2f matrix) {
        staging.clear();
        staging.putFloat(matrix.m00()).putFloat(matrix.m01())
                .putFloat(matrix.m10()).putFloat(matrix.m11());
        staging.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(0.0f).putFloat(0.0f);
        return staging.flip();
    }
}
