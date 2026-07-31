package fr.epistudio.epysia.render.mesh;

import fr.epistudio.epysia.render.backend.BufferDescriptor;
import fr.epistudio.epysia.render.backend.BufferHandle;
import fr.epistudio.epysia.render.backend.BufferUsage;
import fr.epistudio.epysia.render.backend.RenderBackend;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public final class MultiDrawBuffer {

    public static final int COMMAND_BYTES = 20;

    private final RenderBackend backend;
    private final BufferHandle commands;
    private final ByteBuffer staging;
    private final int capacity;
    private int drawCount;

    private MultiDrawBuffer(RenderBackend backend, BufferHandle commands, ByteBuffer staging, int capacity) {
        this.backend = backend;
        this.commands = commands;
        this.staging = staging;
        this.capacity = capacity;
    }

    public static MultiDrawBuffer create(RenderBackend backend, int capacity) {
        ByteBuffer staging = BufferUtils.createByteBuffer(capacity * COMMAND_BYTES);
        BufferHandle handle = backend.createBuffer(new BufferDescriptor(BufferUsage.INDIRECT, staging));
        return new MultiDrawBuffer(backend, handle, staging, capacity);
    }

    public BufferHandle buffer() {
        return commands;
    }

    public int capacity() {
        return capacity;
    }

    public int drawCount() {
        return drawCount;
    }

    public void begin() {
        drawCount = 0;
        staging.clear();
    }

    public boolean append(ArenaMesh allocation) {
        return append(allocation, drawCount);
    }

    public boolean append(ArenaMesh allocation, int perDrawIndex) {
        if (drawCount >= capacity) {
            return false;
        }
        staging.putInt(allocation.indexCount());
        staging.putInt(1);
        staging.putInt(allocation.indexOffset());
        staging.putInt(0);
        staging.putInt(perDrawIndex);
        drawCount++;
        return true;
    }

    public void upload() {
        if (drawCount == 0) {
            return;
        }
        staging.flip();
        backend.writeBuffer(commands, staging, 0L);
        staging.limit(staging.capacity());
    }

    public void destroy() {
        backend.destroy(commands);
    }
}
