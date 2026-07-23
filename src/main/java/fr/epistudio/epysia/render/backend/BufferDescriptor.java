package fr.epistudio.epysia.render.backend;

import java.nio.ByteBuffer;

public record BufferDescriptor(BufferUsage usage, ByteBuffer data, boolean perFrame) {

    public BufferDescriptor(BufferUsage usage, ByteBuffer data) {
        this(usage, data, false);
    }
}
