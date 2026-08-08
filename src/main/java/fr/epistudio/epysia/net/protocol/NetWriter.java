package fr.epistudio.epysia.net.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class NetWriter {
    private static final int CONTINUATION_BIT = 0x80;
    private static final int PAYLOAD_MASK = 0x7F;
    private static final int PAYLOAD_BITS = 7;

    private final ByteBuffer buffer;

    public NetWriter(ByteBuffer buffer) {
        this.buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public static NetWriter allocate(int capacity) {
        return new NetWriter(ByteBuffer.allocate(capacity));
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public int position() {
        return buffer.position();
    }

    public int remaining() {
        return buffer.remaining();
    }

    public NetWriter writeByte(int value) {
        buffer.put((byte) value);
        return this;
    }

    public NetWriter writeBoolean(boolean value) {
        return writeByte(value ? 1 : 0);
    }

    public NetWriter writeMessageType(MessageType type) {
        return writeByte(type.code());
    }

    public NetWriter writeShort(int value) {
        buffer.putShort((short) value);
        return this;
    }

    public NetWriter patchShort(int position, int value) {
        buffer.putShort(position, (short) value);
        return this;
    }

    public NetWriter writeInt(int value) {
        buffer.putInt(value);
        return this;
    }

    public NetWriter writeLong(long value) {
        buffer.putLong(value);
        return this;
    }

    public NetWriter writeFloat(float value) {
        buffer.putFloat(value);
        return this;
    }

    public NetWriter writeVarInt(int value) {
        long remaining = Integer.toUnsignedLong(value);
        while (remaining >= CONTINUATION_BIT) {
            buffer.put((byte) ((remaining & PAYLOAD_MASK) | CONTINUATION_BIT));
            remaining >>>= PAYLOAD_BITS;
        }
        buffer.put((byte) remaining);
        return this;
    }

    public NetWriter writeSignedVarInt(int value) {
        return writeVarInt((value << 1) ^ (value >> 31));
    }

    public NetWriter writeQuantizedFloat(float value, float precision) {
        return writeSignedVarInt(Math.round(value / precision));
    }

    public NetWriter writeString(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(encoded.length);
        buffer.put(encoded);
        return this;
    }

    public NetWriter writeBytes(byte[] source, int offset, int length) {
        buffer.put(source, offset, length);
        return this;
    }

    public NetWriter writeSizedBytes(byte[] source, int offset, int length) {
        writeVarInt(length);
        return writeBytes(source, offset, length);
    }

    public byte[] toByteArray() {
        byte[] copy = new byte[buffer.position()];
        buffer.duplicate().flip().get(copy);
        return copy;
    }

    public ByteBuffer flipped() {
        return buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).flip();
    }

    public NetWriter reset() {
        buffer.clear();
        return this;
    }
}
